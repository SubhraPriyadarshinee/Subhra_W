package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeItemUpdateRequest;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.entity.LabelDownloadEvent;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.LabelDownloadEventMiscInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.model.symbotic.RdcSlotUpdateMessage;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.core.service.LabelDownloadEventService;
import com.walmart.move.nim.receiving.core.service.SlottingServiceImpl;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.LabelDownloadEventStatus;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcLabelGenerationUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcSSTKLabelGenerationUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcSlotUpdateEventProcessorTest {
  @InjectMocks private RdcSlotUpdateEventProcessor rdcSlotUpdateEventProcessor;
  @Mock private LabelDownloadEventService labelDownloadEventService;
  @Mock private RdcSSTKLabelGenerationUtils rdcSSTKLabelGenerationUtils;
  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private RdcLabelGenerationService rdcLabelGenerationService;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private AppConfig appConfig;
  @Mock private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private LabelDataService labelDataService;
  @Mock private SlottingServiceImpl slottingService;
  @Mock private RdcLabelGenerationUtils rdcLabelGenerationUtils;
  private Gson gson;
  private static final String facilityNum = "32679";
  private static final String countryCode = "US";

  private DeliveryDetails deliveryDetails;
  private String deliveryDetailsJsonString;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    gson = new Gson();
    ReflectionTestUtils.setField(rdcSlotUpdateEventProcessor, "gson", gson);
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetailsSSTK.json")
              .getCanonicalPath();

      deliveryDetailsJsonString = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      assert (false);
    }
  }

  @BeforeMethod
  public void reInitData() {
    deliveryDetails =
        JacksonParser.convertJsonToObject(deliveryDetailsJsonString, DeliveryDetails.class);
  }

  @AfterMethod
  public void tearDown() {
    reset(appConfig);
    reset(rdcManagedConfig);
    reset(labelDownloadEventService);
    reset(rdcSSTKLabelGenerationUtils);
    reset(rdcInstructionUtils);
    reset(rdcLabelGenerationService);
    reset(labelDataService);
    reset(hawkeyeRestApiClient);
  }

  @Test
  public void testSlotUpdateEvent_itemPrimeDetails_Success_ConventionalToSymSlot()
      throws ReceivingException {
    RdcSlotUpdateMessage rdcSlotUpdateMessage = getMockRdcSlotUpdateMessage();
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.add(ReceivingConstants.EVENT_TYPE, ReceivingConstants.ITEM_PRIME_DETAILS);
    rdcSlotUpdateMessage.setHttpHeaders(headers);
    List<LabelDownloadEvent> labelDownloadEvents = getMockLabelDownloadEvents();
    labelDownloadEvents.forEach(
        labelDownloadEvent -> {
          labelDownloadEvent.setRejectReason(RejectReason.RDC_SSTK);
        });

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()).thenReturn(14);
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEvents);
    doCallRealMethod()
        .when(rdcSSTKLabelGenerationUtils)
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    doNothing()
        .when(rdcLabelGenerationService)
        .generateAndPublishSSTKLabels(
            anyList(),
            any(DeliveryUpdateMessage.class),
            anyBoolean(),
            any(RdcSlotUpdateMessage.class));
    when(rdcSSTKLabelGenerationUtils.fetchDeliveryDetails(anyString(), eq(94769060L)))
        .thenReturn(deliveryDetails);
    List<DeliveryDocument> deliveryDocumentList = deliveryDetails.getDeliveryDocuments();
    deliveryDocumentList.forEach(
        deliveryDocument -> {
          deliveryDocument.setDeliveryNumber(29905991L);
          deliveryDocument.setPurchaseReferenceNumber("2962730137");
          deliveryDocument
              .getDeliveryDocumentLines()
              .forEach(
                  deliveryDocumentLine -> {
                    ItemData itemData = new ItemData();
                    deliveryDocumentLine.setAdditionalInfo(itemData);
                  });
        });
    deliveryDetails.setDeliveryDocuments(deliveryDocumentList);
    when(rdcSSTKLabelGenerationUtils.fetchDeliveryDetails(anyString(), eq(29905991L)))
        .thenReturn(deliveryDetails);
    when(rdcLabelGenerationService.fetchAndFilterSSTKDeliveryDocuments(
            any(DeliveryDetails.class), any(HttpHeaders.class)))
        .thenReturn(deliveryDetails.getDeliveryDocuments());
    when(rdcSSTKLabelGenerationUtils.isAsrsAlignmentSymEligible(anyString())).thenReturn(true);
    doCallRealMethod()
        .when(rdcLabelGenerationService)
        .buildHeadersForMessagePayload(any(HttpHeaders.class));
    when(rdcLabelGenerationUtils.filterLabelDownloadEventWithPilotDelivery(anyList()))
        .thenReturn(labelDownloadEvents);
    rdcSlotUpdateEventProcessor.processEvent(rdcSlotUpdateMessage);
    verify(rdcSSTKLabelGenerationUtils, times(2))
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    verify(rdcManagedConfig, times(2)).getItemUpdateHawkeyeDeliveriesDayLimit();
    verify(rdcLabelGenerationService, times(2))
        .generateAndPublishSSTKLabels(
            anyList(),
            any(DeliveryUpdateMessage.class),
            anyBoolean(),
            any(RdcSlotUpdateMessage.class));
    verify(appConfig, times(2)).getGdmBaseUrl();
    verify(rdcSSTKLabelGenerationUtils, times(2)).fetchDeliveryDetails(anyString(), anyLong());
    verify(labelDownloadEventService, times(1)).findByItemNumber(anyLong());
  }

  @Test
  public void testSlotUpdateEvent_itemPrimeDetails_Success_SymToConventionalSlot() {
    RdcSlotUpdateMessage rdcSlotUpdateMessage = getMockRdcSlotUpdateMessage();
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    rdcSlotUpdateMessage.setHttpHeaders(headers);
    headers.add(ReceivingConstants.EVENT_TYPE, ReceivingConstants.ITEM_PRIME_DETAILS);
    List<LabelDownloadEvent> labelDownloadEvents = getMockLabelDownloadEvents();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()).thenReturn(14);
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEvents);
    when(labelDataService.findByPurchaseReferenceNumberAndItemNumberAndStatus(
            anyString(), anyLong(), anyString()))
        .thenReturn(Collections.singletonList(getMockLabelDataForSSTK()));
    doNothing()
        .when(rdcLabelGenerationService)
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
    doNothing()
        .when(hawkeyeRestApiClient)
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    doCallRealMethod()
        .when(rdcSSTKLabelGenerationUtils)
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    doCallRealMethod()
        .when(rdcLabelGenerationService)
        .buildHeadersForMessagePayload(any(HttpHeaders.class));
    when(rdcSSTKLabelGenerationUtils.isAsrsAlignmentSymEligible(anyString())).thenReturn(false);
    doCallRealMethod().when(rdcSSTKLabelGenerationUtils).buildPoDeliveryMap(anyList());
    when(rdcLabelGenerationUtils.filterLabelDownloadEventWithPilotDelivery(anyList()))
        .thenReturn(labelDownloadEvents);
    rdcSlotUpdateEventProcessor.processEvent(rdcSlotUpdateMessage);
    verify(rdcSSTKLabelGenerationUtils, times(2))
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    verify(hawkeyeRestApiClient, times(2))
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    verify(labelDownloadEventService, times(1)).findByItemNumber(anyLong());
    verify(rdcLabelGenerationService, times(2))
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
    verify(labelDataService, times(2))
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
  }

  @Test
  public void testSlotUpdateEvent_InvalidEventType() {
    RdcSlotUpdateMessage rdcSlotUpdateMessage = getMockRdcSlotUpdateMessage();
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    rdcSlotUpdateMessage.setHttpHeaders(headers);
    headers.add(ReceivingConstants.EVENT_TYPE, "itemCapacityUpdate");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    rdcSlotUpdateEventProcessor.processEvent(rdcSlotUpdateMessage);
    verify(labelDownloadEventService, times(0)).findByItemNumber(anyLong());
  }

  @Test
  public void testSlotUpdateEvent_itemPrimeDetails_LabelGenerationFailed()
      throws ReceivingException {
    RdcSlotUpdateMessage rdcSlotUpdateMessage = getMockRdcSlotUpdateMessage();
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.add(ReceivingConstants.EVENT_TYPE, ReceivingConstants.ITEM_PRIME_DETAILS);
    rdcSlotUpdateMessage.setHttpHeaders(headers);
    List<LabelDownloadEvent> labelDownloadEvents = getMockLabelDownloadEvents();
    labelDownloadEvents.forEach(
        labelDownloadEvent -> {
          labelDownloadEvent.setRejectReason(RejectReason.RDC_SSTK);
        });
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()).thenReturn(14);
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEvents);
    doCallRealMethod()
        .when(rdcSSTKLabelGenerationUtils)
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    doThrow(new ReceivingException("Mock exception"))
        .when(rdcLabelGenerationService)
        .generateAndPublishSSTKLabels(
            anyList(),
            any(DeliveryUpdateMessage.class),
            anyBoolean(),
            any(RdcSlotUpdateMessage.class));
    when(rdcSSTKLabelGenerationUtils.fetchDeliveryDetails(anyString(), eq(94769060L)))
        .thenReturn(deliveryDetails);
    List<DeliveryDocument> deliveryDocumentList = deliveryDetails.getDeliveryDocuments();
    deliveryDocumentList.forEach(
        deliveryDocument -> {
          deliveryDocument.setDeliveryNumber(29905991L);
          deliveryDocument.setPurchaseReferenceNumber("2962730137");
          deliveryDocument
              .getDeliveryDocumentLines()
              .forEach(
                  deliveryDocumentLine -> {
                    ItemData itemData = new ItemData();
                    deliveryDocumentLine.setAdditionalInfo(itemData);
                  });
        });
    deliveryDetails.setDeliveryDocuments(deliveryDocumentList);
    when(rdcSSTKLabelGenerationUtils.fetchDeliveryDetails(anyString(), eq(29905991L)))
        .thenReturn(deliveryDetails);
    when(rdcLabelGenerationService.fetchAndFilterSSTKDeliveryDocuments(
            any(DeliveryDetails.class), any(HttpHeaders.class)))
        .thenReturn(deliveryDetails.getDeliveryDocuments());
    when(rdcSSTKLabelGenerationUtils.isAsrsAlignmentSymEligible(anyString())).thenReturn(true);
    doCallRealMethod()
        .when(rdcLabelGenerationService)
        .buildHeadersForMessagePayload(any(HttpHeaders.class));
    when(rdcLabelGenerationUtils.filterLabelDownloadEventWithPilotDelivery(anyList()))
        .thenReturn(labelDownloadEvents);
    rdcSlotUpdateEventProcessor.processEvent(rdcSlotUpdateMessage);
    verify(rdcSSTKLabelGenerationUtils, times(2))
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    verify(rdcManagedConfig, times(2)).getItemUpdateHawkeyeDeliveriesDayLimit();
    verify(rdcLabelGenerationService, times(2))
        .generateAndPublishSSTKLabels(
            anyList(),
            any(DeliveryUpdateMessage.class),
            anyBoolean(),
            any(RdcSlotUpdateMessage.class));
    verify(appConfig, times(2)).getGdmBaseUrl();
    verify(rdcSSTKLabelGenerationUtils, times(2)).fetchDeliveryDetails(anyString(), anyLong());
    verify(labelDownloadEventService, times(1)).findByItemNumber(anyLong());
  }

  @Test
  public void testSlotUpdateEvent_itemPrimeDelete_Success_SymSlot() {
    RdcSlotUpdateMessage rdcSlotUpdateMessage = getMockRdcSlotUpdateMessage();
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    rdcSlotUpdateMessage.setHttpHeaders(headers);
    headers.add(ReceivingConstants.EVENT_TYPE, ReceivingConstants.ITEM_PRIME_DELETE);
    List<LabelDownloadEvent> labelDownloadEvents = getMockLabelDownloadEvents();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()).thenReturn(14);
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEvents);
    when(labelDataService.findByPurchaseReferenceNumberAndItemNumberAndStatus(
            anyString(), anyLong(), anyString()))
        .thenReturn(Collections.singletonList(getMockLabelDataForSSTK()));
    doNothing()
        .when(rdcLabelGenerationService)
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
    doNothing()
        .when(hawkeyeRestApiClient)
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    doCallRealMethod()
        .when(rdcSSTKLabelGenerationUtils)
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    doCallRealMethod()
        .when(rdcLabelGenerationService)
        .buildHeadersForMessagePayload(any(HttpHeaders.class));
    when(rdcSSTKLabelGenerationUtils.isAsrsAlignmentSymEligible(anyString())).thenReturn(true);
    doCallRealMethod().when(rdcSSTKLabelGenerationUtils).buildPoDeliveryMap(anyList());
    when(rdcLabelGenerationUtils.filterLabelDownloadEventWithPilotDelivery(anyList()))
        .thenReturn(labelDownloadEvents);
    rdcSlotUpdateEventProcessor.processEvent(rdcSlotUpdateMessage);
    verify(rdcSSTKLabelGenerationUtils, times(2))
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    verify(hawkeyeRestApiClient, times(2))
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    verify(labelDownloadEventService, times(1)).findByItemNumber(anyLong());
    verify(rdcLabelGenerationService, times(2))
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
    verify(labelDataService, times(2))
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
  }

  @Test
  public void testSlotUpdateEvent_itemPrimeDelete_Success_SymSlot_RejectReason_Breakout() {
    RdcSlotUpdateMessage rdcSlotUpdateMessage = getMockRdcSlotUpdateMessage();
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    rdcSlotUpdateMessage.setHttpHeaders(headers);
    headers.add(ReceivingConstants.EVENT_TYPE, ReceivingConstants.ITEM_PRIME_DELETE);
    List<LabelDownloadEvent> labelDownloadEvents = getMockLabelDownloadEvents();
    labelDownloadEvents.forEach(
        labelDownloadEvent -> {
          labelDownloadEvent.setRejectReason(RejectReason.BREAKOUT);
        });
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()).thenReturn(14);
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEvents);
    when(labelDataService.findByPurchaseReferenceNumberAndItemNumberAndStatus(
            anyString(), anyLong(), anyString()))
        .thenReturn(Collections.singletonList(getMockLabelDataForSSTK()));
    doNothing()
        .when(rdcLabelGenerationService)
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
    doNothing()
        .when(hawkeyeRestApiClient)
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    doCallRealMethod()
        .when(rdcSSTKLabelGenerationUtils)
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    doCallRealMethod()
        .when(rdcLabelGenerationService)
        .buildHeadersForMessagePayload(any(HttpHeaders.class));
    when(rdcSSTKLabelGenerationUtils.isAsrsAlignmentSymEligible(anyString())).thenReturn(true);
    when(rdcLabelGenerationUtils.filterLabelDownloadEventWithPilotDelivery(anyList()))
        .thenReturn(labelDownloadEvents);
    rdcSlotUpdateEventProcessor.processEvent(rdcSlotUpdateMessage);
    verify(rdcSSTKLabelGenerationUtils, times(2))
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    verify(hawkeyeRestApiClient, times(2))
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    verify(labelDownloadEventService, times(1)).findByItemNumber(anyLong());
    verify(rdcLabelGenerationService, times(0))
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
    verify(labelDataService, times(0))
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
  }

  @Test
  public void testSlotUpdateEvent_itemPrimeDelete_Success_ConventionalSlot() {
    RdcSlotUpdateMessage rdcSlotUpdateMessage = getMockRdcSlotUpdateMessage();
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    rdcSlotUpdateMessage.setHttpHeaders(headers);
    headers.add(ReceivingConstants.EVENT_TYPE, ReceivingConstants.ITEM_PRIME_DELETE);
    List<LabelDownloadEvent> labelDownloadEvents = getMockLabelDownloadEvents();
    labelDownloadEvents.forEach(
        labelDownloadEvent -> {
          labelDownloadEvent.setRejectReason(RejectReason.RDC_SSTK);
        });
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()).thenReturn(14);
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEvents);
    when(labelDataService.findByPurchaseReferenceNumberAndItemNumberAndStatus(
            anyString(), anyLong(), anyString()))
        .thenReturn(Collections.singletonList(getMockLabelDataForSSTK()));
    doNothing()
        .when(rdcLabelGenerationService)
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
    doNothing()
        .when(hawkeyeRestApiClient)
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    doCallRealMethod()
        .when(rdcSSTKLabelGenerationUtils)
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    doCallRealMethod()
        .when(rdcLabelGenerationService)
        .buildHeadersForMessagePayload(any(HttpHeaders.class));
    when(rdcSSTKLabelGenerationUtils.isAsrsAlignmentSymEligible(anyString())).thenReturn(false);
    rdcSlotUpdateEventProcessor.processEvent(rdcSlotUpdateMessage);
    verify(rdcSSTKLabelGenerationUtils, times(2))
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    verify(hawkeyeRestApiClient, times(0))
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    verify(labelDownloadEventService, times(1)).findByItemNumber(anyLong());
    verify(rdcLabelGenerationService, times(0))
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
    verify(labelDataService, times(0))
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
  }

  private RdcSlotUpdateMessage getMockRdcSlotUpdateMessage() {
    return RdcSlotUpdateMessage.builder()
        .primeSlotId(RdcConstants.SYMCP_SLOT)
        .asrsAlignment(ReceivingConstants.SYM_SYSTEM_DEFAULT_VALUE)
        .itemNbr(566051127L)
        .build();
  }

  private List<LabelDownloadEvent> getMockLabelDownloadEvents() {
    List<LabelDownloadEvent> labelDownloadEvents = new ArrayList<>();
    LabelDownloadEventMiscInfo labelDownloadEventMiscInfo =
        LabelDownloadEventMiscInfo.builder().labelType("SSTK").build();
    labelDownloadEvents.add(
        LabelDownloadEvent.builder()
            .deliveryNumber(94769060L)
            .itemNumber(566051127L)
            .purchaseReferenceNumber("3615852071")
            .createTs(new Date())
            .status(LabelDownloadEventStatus.PROCESSED.toString())
            .miscInfo(gson.toJson(labelDownloadEventMiscInfo))
            .build());
    labelDownloadEvents.add(
        LabelDownloadEvent.builder()
            .deliveryNumber(29905991L)
            .itemNumber(566051127L)
            .purchaseReferenceNumber("2962730137")
            .createTs(new Date())
            .status(LabelDownloadEventStatus.PROCESSED.toString())
            .miscInfo(gson.toJson(labelDownloadEventMiscInfo))
            .build());
    return labelDownloadEvents;
  }

  private LabelData getMockLabelDataForSSTK() {
    LabelData labelData = new LabelData();
    labelData.setPurchaseReferenceNumber("5232232323");
    labelData.setPurchaseReferenceLineNumber(1);
    labelData.setDeliveryNumber(232323323L);
    labelData.setItemNumber(566051127L);
    labelData.setTrackingId("c060200000100000025796158");
    labelData.setStatus("AVAILABLE");
    labelData.setLabelSequenceNbr(20231016000100001L);
    return labelData;
  }

  private SlottingPalletResponse getSlottingPalletResponse() {
    SlottingPalletResponse slottingPalletResponse = new SlottingPalletResponse();
    List<SlottingDivertLocations> slottingDivertLocationsList = new ArrayList<>();
    SlottingDivertLocations slottingDivertLocations = new SlottingDivertLocations();
    slottingDivertLocations.setPrimeLocation("A0002");
    slottingDivertLocations.setLocationSize(72);
    slottingDivertLocations.setSlotType("PRIME");
    slottingDivertLocations.setItemNbr(566051127L);
    slottingDivertLocations.setAsrsAlignment("SYM2_5");
    slottingDivertLocationsList.add(slottingDivertLocations);
    slottingPalletResponse.setLocations(slottingDivertLocationsList);
    return slottingPalletResponse;
  }
}
