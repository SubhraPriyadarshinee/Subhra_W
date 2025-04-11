package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.message.publisher.JMSLabelDataPublisher;
import com.walmart.move.nim.receiving.acc.mock.data.MockLabelData;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.contract.prelabel.LabelingService;
import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.message.common.HawkeyeItemUpdateType;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.label.FormattedLabels;
import com.walmart.move.nim.receiving.core.model.label.PossibleUPC;
import com.walmart.move.nim.receiving.core.model.label.RejectReason;
import com.walmart.move.nim.receiving.core.model.label.ScanItem;
import com.walmart.move.nim.receiving.core.service.DeliveryEventPersisterService;
import com.walmart.move.nim.receiving.core.service.LPNCacheService;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class GenericLabelGeneratorServiceTest extends ReceivingTestBase {

  @InjectMocks private GenericLabelGeneratorService genericLabelGeneratorService;

  @Mock private LPNCacheService lpnCacheService;

  @Mock private LabelDataService labelDataService;

  @Mock private DeliveryEventPersisterService deliveryEventPersisterService;

  @Mock private LabelingService labelingService;

  @Mock private ACCManagedConfig accManagedConfig;

  @Mock private JmsPublisher jmsPublisher;

  @Mock private AsyncLabelPersisterService asyncLabelPersisterService;

  @Mock private LabelingHelperService labelingHelperService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private JMSLabelDataPublisher jmsLabelDataPublisher;

  @Captor private ArgumentCaptor<List<LabelData>> labelDataCaptor;

  private DeliveryEvent deliveryEvent;

  private DeliveryDetails deliveryDetails;

  private final String[] lpnSet1 = {
    "c32987000000000000000001",
    "c32987000000000000000002",
    "c32987000000000000000003",
    "c32987000000000000000004",
    "c32987000000000000000005",
    "c32987000000000000000006"
  };

  private final String[] lpnSet2 = {"c32987000000000000000007"};

  private String aclLabelDataPayloadSchemaFilePath;

  private String deliveryDetailsJsonString;

  private final Gson gson = new Gson();

  @BeforeClass
  public void initMocks() throws ReceivingException {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(genericLabelGeneratorService, "gson", gson);
    deliveryEvent =
        DeliveryEvent.builder()
            .id(1)
            .eventStatus(EventTargetStatus.IN_PROGRESS)
            .deliveryNumber(94769060L)
            .url("https://delivery.test")
            .retriesCount(0)
            .build();
    TenantContext.setFacilityNum(54321);
    TenantContext.setFacilityCountryCode("us");

    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json")
              .getCanonicalPath();

      aclLabelDataPayloadSchemaFilePath =
          new File("../../receiving-test/src/main/resources/jsonSchema/aclLabelDataPayload.json")
              .getCanonicalPath();

      deliveryDetailsJsonString = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      assert (false);
    }
    doReturn(Arrays.asList(lpnSet1)).when(lpnCacheService).getLPNSBasedOnTenant(anyInt(), any());
    when(labelDataService.saveAll(anyList())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    doReturn("")
        .when(labelingService)
        .getFormattedLabelData(any(), any(), anyString(), anyString(), anyString());
    doReturn(null).when(accManagedConfig).getRcvBaseUrl();
  }

  @BeforeMethod
  public void reInitData() {
    deliveryDetails =
        JacksonParser.convertJsonToObject(deliveryDetailsJsonString, DeliveryDetails.class);
  }

  @AfterMethod
  public void resetMocks() {
    reset(lpnCacheService);
    reset(labelDataService);
    reset(deliveryEventPersisterService);
    reset(labelingService);
    reset(asyncLabelPersisterService);
    reset(jmsPublisher);
    reset(labelingHelperService);
    reset(tenantSpecificConfigReader);
    reset(jmsLabelDataPublisher);
  }

  private DeliveryDetails getDeliveryDetailsFromPath(String path) {
    DeliveryDetails deliveryDetails = null;
    try {
      String dataPath = new File(path).getCanonicalPath();
      deliveryDetails =
          JacksonParser.convertJsonToObject(
              new String(Files.readAllBytes(Paths.get(dataPath))), DeliveryDetails.class);
    } catch (IOException e) {
      return null;
    }
    return deliveryDetails;
  }

  @Test
  public void testGenerateGenericLabel_NotAValidPreLabelEvent() throws ReceivingException {
    deliveryEvent.setEventType("SCHEDULED");
    genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    verify(labelDataService, times(0)).saveAll(anyList());
  }

  @Test
  public void testGenerateGenericLabel_PostToLabelingEnabledDoorAssignEventInProgress()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    doReturn(true).when(accManagedConfig).isLabelPostEnabled();
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));

    genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    verify(labelDataService, times(0))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(labelDataService, times(1)).saveAll(anyList());
    verify(asyncLabelPersisterService, times(1))
        .publishLabelDataToLabelling(any(), anyList(), any());
  }

  @Test
  public void testGenerateGenericLabel_DoorAssignEventInProgress() throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    doNothing()
        .when(asyncLabelPersisterService)
        .publishLabelDataToLabelling(any(), anyList(), any());
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));

    genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    verify(labelDataService, times(0))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(labelDataService, times(1)).saveAll(anyList());
  }

  @Test
  public void testGenerateGenericLabel_DoorAssignEventInProgressAndPoInCancelledStatus()
      throws ReceivingException {
    DeliveryDetails deliveryDetailsWithRejectedPO =
        getDeliveryDetailsFromPath(
            "../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json");
    deliveryDetailsWithRejectedPO
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(POStatus.CNCL.name());
    deliveryEvent.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));

    genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetailsWithRejectedPO);
    verify(labelDataService, times(0))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(labelDataService, times(1)).saveAll(Collections.emptyList());
  }

  @Test
  public void testGenerateGenericLabel_DoorAssignEventInProgressAndPoLineInCancelledStatus()
      throws ReceivingException {
    DeliveryDetails deliveryDetailsWithRejectedPO =
        getDeliveryDetailsFromPath(
            "../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json");
    deliveryDetailsWithRejectedPO
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.name());
    deliveryEvent.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));

    genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetailsWithRejectedPO);
    verify(labelDataService, times(0))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(labelDataService, times(1)).saveAll(Collections.emptyList());
  }

  @Test
  public void testGenerateGenericLabel_DoorAssignEventInProgressAndPoLineInRejectedStatus()
      throws ReceivingException {
    DeliveryDetails deliveryDetailsWithRejectedPO =
        getDeliveryDetailsFromPath(
            "../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json");
    deliveryDetailsWithRejectedPO
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getOperationalInfo()
        .setState(POLineStatus.REJECTED.name());
    deliveryEvent.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));

    genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetailsWithRejectedPO);
    verify(labelDataService, times(0))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(labelDataService, times(1)).saveAll(Collections.emptyList());
  }

  @Test
  public void testGenerateGenericLabel_DoorAssignEventGoesToDeleteState()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryEvent.setEventStatus(EventTargetStatus.DELETE);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));

    genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    verify(labelDataService, times(0))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(labelDataService, times(0)).saveAll(anyList());
  }

  @Test
  public void testGenerateGenericLabel_FallBackEvent() throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.PRE_LABEL_GEN_FALLBACK);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    doReturn(new ArrayList<>())
        .when(labelDataService)
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));

    genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    verify(labelDataService, times(1))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(labelDataService, times(1)).saveAll(anyList());
  }

  @Test
  public void testGenerateGenericLabel_PostToLabelingEnabledAndFallBackEvent()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.PRE_LABEL_GEN_FALLBACK);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    doReturn(new ArrayList<>())
        .when(labelDataService)
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    doNothing()
        .when(asyncLabelPersisterService)
        .publishLabelDataToLabelling(any(), anyList(), any());
    doReturn(true).when(accManagedConfig).isLabelPostEnabled();
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));

    genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    verify(labelDataService, times(1))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(labelDataService, times(1)).saveAll(anyList());
    verify(asyncLabelPersisterService, times(1))
        .publishLabelDataToLabelling(any(), anyList(), any());
  }

  @Test
  public void testGenerateGenericLabel_FallBackEventGoesToDeleteStat() throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.PRE_LABEL_GEN_FALLBACK);
    deliveryEvent.setEventStatus(EventTargetStatus.DELETE);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    doReturn(new ArrayList<>())
        .when(labelDataService)
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));

    genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    verify(labelDataService, times(1))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(labelDataService, times(0)).saveAll(anyList());
  }

  @Test
  public void testGenerateGenericLabel_RefreshCatalogGtinCheckForConveyable()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    LabelData labelData = MockLabelData.getMockHawkEyeLabelData();
    PossibleUPC possibleUPC = gson.fromJson(labelData.getPossibleUPC(), PossibleUPC.class);
    labelData.setPossibleUPC(
        gson.toJson(
            PossibleUPC.builder()
                .sscc(null)
                .catalogGTIN("")
                .consumableGTIN(possibleUPC.getConsumableGTIN())
                .orderableGTIN(possibleUPC.getOrderableGTIN())
                .build()));
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(labelData);
    doReturn(labelDataList)
        .when(labelDataService)
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>> genericLabDeliveryEventMapPair =
        genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    LabelData updatedLabelData1 =
        genericLabDeliveryEventMapPair.getValue().entrySet().iterator().next().getValue().get(0);
    PossibleUPC possibleUPC1 = gson.fromJson(updatedLabelData1.getPossibleUPC(), PossibleUPC.class);

    assertNotNull(possibleUPC1.getCatalogGTIN());
  }

  @Test
  public void testGenerateGenericLabel_RefreshCatalogGtinCheckForNonConveyable()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    LabelData labelData = MockLabelData.getMockHawkEyeLabelData();
    PossibleUPC possibleUPC = gson.fromJson(labelData.getPossibleUPC(), PossibleUPC.class);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(false);
    labelData.setPossibleUPC(
        gson.toJson(
            PossibleUPC.builder()
                .sscc(null)
                .catalogGTIN("")
                .consumableGTIN(possibleUPC.getConsumableGTIN())
                .orderableGTIN(possibleUPC.getOrderableGTIN())
                .build()));
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(labelData);
    doReturn(labelDataList)
        .when(labelDataService)
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>> genericLabDeliveryEventMapPair =
        genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    LabelData updatedLabelData1 =
        genericLabDeliveryEventMapPair.getValue().entrySet().iterator().next().getValue().get(0);
    PossibleUPC possibleUPC1 = gson.fromJson(updatedLabelData1.getPossibleUPC(), PossibleUPC.class);

    assertNotNull(possibleUPC1.getCatalogGTIN());
    assertFalse(updatedLabelData1.getIsDAConveyable());
  }

  @Test
  public void testGenerateGenericLabel_RejectCodeCheckForDoorAssignedDANonCon()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(false);
    Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>> genericLabDeliveryEventMapPair =
        genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    LabelData labelDataForDaNoncon =
        genericLabDeliveryEventMapPair.getValue().entrySet().iterator().next().getValue().get(0);
    assertEquals(RejectReason.NONCON_DA, labelDataForDaNoncon.getRejectReason());
    assertFalse(labelDataForDaNoncon.getIsDAConveyable());
  }

  @Test
  public void testGenerateGenericLabel_RejectCodeCheckForDoorAssignedSSTKNonCon()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    List<String> activeChannelMethods = new ArrayList<>();
    activeChannelMethods.add(PurchaseReferenceType.SSTKU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(false);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType(PurchaseReferenceType.SSTKU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(activeChannelMethods);
    Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>> genericLabDeliveryEventMapPair =
        genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    LabelData labelDataForSSTKNoncon =
        genericLabDeliveryEventMapPair.getValue().entrySet().iterator().next().getValue().get(0);
    assertEquals(RejectReason.NONCON_SSTK, labelDataForSSTKNoncon.getRejectReason());
    assertFalse(labelDataForSSTKNoncon.getIsDAConveyable());
  }

  @Test
  public void testGenerateGenericLabel_RejectCodeCheckForDoorAssignedSSTKCon()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    List<String> activeChannelMethods = new ArrayList<>();
    activeChannelMethods.add(PurchaseReferenceType.SSTKU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(true);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType(PurchaseReferenceType.SSTKU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(activeChannelMethods);
    Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>> genericLabDeliveryEventMapPair =
        genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    LabelData labelDataForSSTKCon =
        genericLabDeliveryEventMapPair.getValue().entrySet().iterator().next().getValue().get(0);
    assertEquals(RejectReason.CONVEYABLE_SSTK, labelDataForSSTKCon.getRejectReason());
  }

  @Test
  public void testGenerateGenericLabel_RejectCodeCheckForUpdateEventsDANonCon()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    LabelData labelData = MockLabelData.getMockHawkEyeLabelData();
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(false);
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(labelData);
    doReturn(labelDataList)
        .when(labelDataService)
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>> genericLabDeliveryEventMapPair =
        genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    LabelData labelDataForDaNoncon1 =
        genericLabDeliveryEventMapPair.getValue().entrySet().iterator().next().getValue().get(0);
    assertEquals(RejectReason.NONCON_DA, labelDataForDaNoncon1.getRejectReason());
    assertFalse(labelDataForDaNoncon1.getIsDAConveyable());
  }

  @Test
  public void testGenerateGenericLabel_RejectCodeCheckForUpdateEventSSTKNonCon()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    LabelData labelData = MockLabelData.getMockHawkEyeLabelData();
    List<String> activeChannelMethods = new ArrayList<>();
    activeChannelMethods.add(PurchaseReferenceType.SSTKU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(false);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType(PurchaseReferenceType.SSTKU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(activeChannelMethods);
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(labelData);
    doReturn(labelDataList)
        .when(labelDataService)
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>> genericLabDeliveryEventMapPair =
        genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    LabelData labelDataForSSTKNoncon1 =
        genericLabDeliveryEventMapPair.getValue().entrySet().iterator().next().getValue().get(0);
    assertEquals(RejectReason.NONCON_SSTK, labelDataForSSTKNoncon1.getRejectReason());
    assertFalse(labelDataForSSTKNoncon1.getIsDAConveyable());
  }

  @Test
  public void testGenerateGenericLabel_RejectCodeCheckForUpdateEventSSTKCon()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    LabelData labelData = MockLabelData.getMockHawkEyeLabelData();
    List<String> activeChannelMethods = new ArrayList<>();
    activeChannelMethods.add(PurchaseReferenceType.SSTKU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(true);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType(PurchaseReferenceType.SSTKU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(activeChannelMethods);
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(labelData);
    doReturn(labelDataList)
        .when(labelDataService)
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>> genericLabDeliveryEventMapPair =
        genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    LabelData labelDataForSSTKCon =
        genericLabDeliveryEventMapPair.getValue().entrySet().iterator().next().getValue().get(0);
    assertEquals(RejectReason.CONVEYABLE_SSTK, labelDataForSSTKCon.getRejectReason());
  }

  @Test
  public void testGenerateGenericLabel_RejectCodeCheckForUpdateEventSSTKConWithActiveChannelCROSSU()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    LabelData labelData = MockLabelData.getMockHawkEyeLabelData();
    List<String> activeChannelMethods = new ArrayList<>();
    activeChannelMethods.add(PurchaseReferenceType.CROSSU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(true);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType(PurchaseReferenceType.SSTKU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(activeChannelMethods);
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(labelData);
    doReturn(labelDataList)
        .when(labelDataService)
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>> genericLabDeliveryEventMapPair =
        genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    LabelData labelDataForSSTKCon =
        genericLabDeliveryEventMapPair.getValue().entrySet().iterator().next().getValue().get(0);
    assertNull(labelDataForSSTKCon.getRejectReason());
  }

  @Test
  public void
      testGenerateGenericLabel_RejectCodeCheckForUpdateEventSSTKConWithActiveChannelCROSSUAndSSTKU()
          throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    LabelData labelData = MockLabelData.getMockHawkEyeLabelData();
    List<String> activeChannelMethods = new ArrayList<>();
    activeChannelMethods.add(PurchaseReferenceType.CROSSU.name());
    activeChannelMethods.add(PurchaseReferenceType.SSTKU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(true);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType(PurchaseReferenceType.SSTKU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(activeChannelMethods);
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(labelData);
    doReturn(labelDataList)
        .when(labelDataService)
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>> genericLabDeliveryEventMapPair =
        genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    LabelData labelDataForSSTKCon =
        genericLabDeliveryEventMapPair.getValue().entrySet().iterator().next().getValue().get(0);
    assertEquals(RejectReason.CONVEYABLE_SSTK, labelDataForSSTKCon.getRejectReason());
  }

  @Test
  public void
      testGenerateGenericLabel_RejectCodeCheckForUpdateEventSSTKNonConWithActiveChannelCROSSUAndSSTKU()
          throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    LabelData labelData = MockLabelData.getMockHawkEyeLabelData();
    List<String> activeChannelMethods = new ArrayList<>();
    activeChannelMethods.add(PurchaseReferenceType.CROSSU.name());
    activeChannelMethods.add(PurchaseReferenceType.SSTKU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(false);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType(PurchaseReferenceType.SSTKU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(activeChannelMethods);
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(labelData);
    doReturn(labelDataList)
        .when(labelDataService)
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>> genericLabDeliveryEventMapPair =
        genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    LabelData labelDataForSSTKCon =
        genericLabDeliveryEventMapPair.getValue().entrySet().iterator().next().getValue().get(0);
    assertEquals(RejectReason.NONCON_SSTK, labelDataForSSTKCon.getRejectReason());
  }

  @Test
  public void testGenerateGenericLabel_RejectCodeCheckForDoorAssignedSSTKConWithNoActiveChannel()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    List<String> activeChannelMethods = new ArrayList<>();
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(true);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType(PurchaseReferenceType.SSTKU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(activeChannelMethods);
    Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>> genericLabDeliveryEventMapPair =
        genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    LabelData labelDataForSSTKCon =
        genericLabDeliveryEventMapPair.getValue().entrySet().iterator().next().getValue().get(0);
    assertEquals(RejectReason.CONVEYABLE_SSTK, labelDataForSSTKCon.getRejectReason());
  }

  @Test
  public void testGenerateGenericLabel_RejectCodeCheckForDoorAssignedEventDACon()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    List<String> activeChannelMethods = new ArrayList<>();
    activeChannelMethods.add(PurchaseReferenceType.CROSSU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(true);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType(PurchaseReferenceType.CROSSU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(activeChannelMethods);
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));
    Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>> genericLabDeliveryEventMapPair =
        genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    LabelData labelDataForDACon =
        genericLabDeliveryEventMapPair.getValue().entrySet().iterator().next().getValue().get(0);
    assertNull(labelDataForDACon.getRejectReason());
  }

  @Test
  public void testGenerateGenericLabel_RejectCodeCheckForUpdateEventDACon()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    LabelData labelData = MockLabelData.getMockHawkEyeLabelData();
    List<String> activeChannelMethods = new ArrayList<>();
    activeChannelMethods.add(PurchaseReferenceType.CROSSU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(true);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType(PurchaseReferenceType.CROSSU.name());
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(activeChannelMethods);
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(labelData);
    doReturn(labelDataList)
        .when(labelDataService)
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));
    Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>> genericLabDeliveryEventMapPair =
        genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);
    LabelData labelDataForDACon =
        genericLabDeliveryEventMapPair.getValue().entrySet().iterator().next().getValue().get(0);
    assertNull(labelDataForDACon.getRejectReason());
  }

  @Test
  public void testPublishACLLabelData() throws IOException {
    doReturn(
            Arrays.asList(
                MockLabelData.getMockLabelData(), MockLabelData.getMockExceptionLabelData()))
        .when(labelDataService)
        .getLabelDataByDeliveryNumberSortedBySeq(anyLong());
    doReturn(MockLabelData.getMockScanItem())
        .when(labelingHelperService)
        .buildScanItemFromLabelData(anyLong(), any(LabelData.class));
    doReturn(MockLabelData.getFormattedLabels())
        .when(labelingHelperService)
        .buildFormattedLabel(any(LabelData.class));
    doReturn(jmsLabelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));

    genericLabelGeneratorService.publishACLLabelDataForDelivery(
        94769060L, MockHttpHeaders.getHeaders());
    ArgumentCaptor<com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData> captor =
        ArgumentCaptor.forClass(
            com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData.class);
    verify(jmsLabelDataPublisher, times(1)).publish(captor.capture(), any());
    assertEquals(captor.getValue(), MockLabelData.getMockACLLabelDataTO());
    assertTrue(
        validateContract(
            new String(Files.readAllBytes(Paths.get(aclLabelDataPayloadSchemaFilePath))),
            JacksonParser.writeValueAsString(captor.getValue())));
  }

  @Test
  public void testPublishACLLabelDataDelta() throws IOException {
    DeliveryDetails deliveryDetails =
        getDeliveryDetailsFromPath(
            "../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json");
    ScanItem mockScanItem = MockLabelData.getMockScanItem();
    doReturn(mockScanItem)
        .when(labelingHelperService)
        .buildScanItemFromLabelData(anyLong(), any(LabelData.class));
    doReturn(MockLabelData.getFormattedLabels())
        .when(labelingHelperService)
        .buildFormattedLabel(any(LabelData.class));
    doReturn(jmsLabelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));

    genericLabelGeneratorService.publishACLLabelDataDelta(
        Collections.singletonMap(
            deliveryDetails.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0),
            Collections.singletonList(MockLabelData.getMockLabelData())),
        94769060L,
        "",
        "322683",
        MockHttpHeaders.getHeaders());
    ArgumentCaptor<com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData> captor =
        ArgumentCaptor.forClass(
            com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData.class);
    verify(jmsLabelDataPublisher, times(1)).publish(captor.capture(), any());
    assertTrue(
        validateContract(
            new String(Files.readAllBytes(Paths.get(aclLabelDataPayloadSchemaFilePath))),
            JacksonParser.writeValueAsString(captor.getValue())));
  }

  @Test
  public void generateExceptionLabel_success() throws ReceivingException {
    doReturn(MockLabelData.getMockLabelData())
        .when(labelDataService)
        .findByDeliveryNumberAndUPCAndLabelType(anyLong(), anyString(), any());
    doReturn(Arrays.asList(lpnSet2)).when(lpnCacheService).getLPNSBasedOnTenant(anyInt(), any());
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));

    FormattedLabels aclFormattedLabels =
        genericLabelGeneratorService.generateExceptionLabel(94769060L, "10074451115207");
    assertEquals(aclFormattedLabels.getLpns().size(), 1);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Pre generated data for delivery and UPC not found")
  public void generateExceptionLabel_failure() throws ReceivingException {
    doReturn(null)
        .when(labelDataService)
        .findByDeliveryNumberAndUPCAndLabelType(anyLong(), anyString(), any());
    doReturn(Arrays.asList(lpnSet2)).when(lpnCacheService).getLPNSBasedOnTenant(anyInt(), any());
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));

    genericLabelGeneratorService.generateExceptionLabel(94769060L, "10074451115207");
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Pre generated data for delivery and UPC not found")
  public void generateExceptionLabel_failureNonCon() throws ReceivingException {
    doReturn(MockLabelData.getMockLabelDataNonCon())
        .when(labelDataService)
        .findByDeliveryNumberAndUPCAndLabelType(anyLong(), anyString(), any());
    doReturn(Arrays.asList(lpnSet2)).when(lpnCacheService).getLPNSBasedOnTenant(anyInt(), any());
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));

    genericLabelGeneratorService.generateExceptionLabel(94769060L, "10074451115207");
  }

  @Test
  public void testUpdateLabelDataForItemUpdate() throws ReceivingException {
    HawkeyeItemUpdateType eventType = HawkeyeItemUpdateType.CROSSU_TO_SSTKU_DELIVERY;
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(Collections.singletonList(PurchaseReferenceType.SSTKU.name()));
    doReturn(Collections.singletonList(MockLabelData.getMockLabelData()))
        .when(labelDataService)
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    when(labelDataService.saveAll(anyList())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    doReturn(true).when(accManagedConfig).isLabelPostEnabled();
    genericLabelGeneratorService.updateLabelDataForItemUpdate(
        deliveryDetails, eventType, Boolean.TRUE);
    verify(labelDataService, times(1))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(labelDataService, times(1)).saveAll(anyList());
  }

  @Test
  public void testGenerateGenericLabel_PoLineUpdated_LabelDataLpnTableEnabled()
      throws ReceivingException {
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);

    doReturn(Collections.singletonList(MockLabelData.getMockLabelData()))
        .when(labelDataService)
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    doReturn(Arrays.asList(lpnSet2)).when(lpnCacheService).getLPNSBasedOnTenant(anyInt(), any());
    doReturn(gson.toJsonTree(0.15f))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ACCConstants.EXCEPTION_LPN_THRESHOLD));
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_INSERTION_ENABLED);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventById(anyLong());
    doNothing()
        .when(asyncLabelPersisterService)
        .publishLabelDataToLabelling(any(), anyList(), any());

    // PO line updated
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setExpectedQty(7);

    genericLabelGeneratorService.generateGenericLabel(deliveryEvent, deliveryDetails);

    verify(labelDataService, times(1))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(tenantSpecificConfigReader, times(1))
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_INSERTION_ENABLED);
    verify(labelDataService, times(1)).saveAll(labelDataCaptor.capture());
    List<LabelData> labelDataList = labelDataCaptor.getValue();
    assertEquals(
        labelDataList.get(0).getLabelDataLpnList().get(0).getLpn(), "c32987000000000000000007");
  }
}
