package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.EventType;
import com.walmart.move.nim.receiving.core.common.SymboticPutawayPublishHelper;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.publisher.JMSSorterPublisher;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.instruction.*;
import com.walmart.move.nim.receiving.core.model.symbotic.SymFreightType;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockInstructionDownloadEvent;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdcInstruction;
import com.walmart.move.nim.receiving.rdc.model.LabelAction;
import com.walmart.move.nim.receiving.rdc.utils.RdcContainerUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcOfflineRecieveUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.nio.charset.StandardCharsets;
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

/** Unit test cases for RdcOfflineReceiveService class */
public class RdcOfflineReceiveServiceTest {
  @InjectMocks private RdcOfflineReceiveService rdcOfflineReceiveService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RdcContainerUtils rdcContainerUtils;
  @Mock private ReceiptService receiptService;
  @Mock private RdcInstructionHelper rdcInstructionHelper;
  @Mock private RdcReceivingUtils rdcReceivingUtils;
  @Mock private LabelDataService labelDataService;
  @Mock private VendorBasedDeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler;

  @Mock private RdcDaService rdcDaService;
  @Mock private RdcOfflineRecieveUtils rdcOfflineRecieveUtils;
  @Mock AppConfig appConfig;
  @Mock private RdcInstructionUtils rdcInstructionUtils;

  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private JMSSorterPublisher jmsSorterPublisher;
  @Mock private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Mock private InstructionHelperService instructionHelperService;

  private HttpHeaders httpHeaders;
  private String facilityNum = "32818";
  private String facilityCountryCode = "us";
  private Gson gson;
  private final List<String> VALID_ASRS_LIST =
      Arrays.asList(
          ReceivingConstants.SYM_BRKPK_ASRS_VALUE, ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);

  @BeforeClass
  public void initMocks() {
    gson = new Gson();
    MockitoAnnotations.initMocks(this);
    // ReflectionTestUtils.setField(rdcOfflineReceiveService, "gson", gson);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode(facilityCountryCode);
    TenantContext.setCorrelationId("2323-323dsds-323dwsd-3d23e");
  }

  @BeforeMethod
  public void setup() {
    httpHeaders = MockHttpHeaders.getHeaders(facilityNum, facilityCountryCode);
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "23");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-23");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "0086623");
    ReflectionTestUtils.setField(rdcOfflineReceiveService, "labelDataBatchSize", 20);
  }

  @AfterMethod
  public void tearDown() {
    reset(
        rdcContainerUtils,
        receiptService,
        rdcInstructionHelper,
        rdcReceivingUtils,
        labelDataService,
        rdcDaService,
        appConfig);
  }

  @Test
  public void testAutoReceiveContainersForOfflineRcvSuccessWithChild() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());

    List<ReceivedContainer> receivedContainers = mockReceivedContainersWithChild();

    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("E06938000020267142"));
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    InstructionDownloadBlobDataDTO instructionDownloadBlobDataDTO =
        new InstructionDownloadBlobDataDTO();
    instructionDownloadBlobDataDTO.setProjectedQty(12);
    instructionDownloadBlobDataDTO.setDeliveryNbr(2343427L);
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    deliveryDocument.setTrackingId("E06938000020267142");
    deliveryDocument.getDeliveryDocumentLines().get(0).setChildTrackingId("5000000566385787");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId("E06938000020267142");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcvWithChild();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    doReturn(receivedContainers.get(0))
        .when(rdcDaService)
        .buildReceivedContainer(
            any(LabelData.class),
            any(DeliveryDocument.class),
            any(DeliveryDocumentLine.class),
            anyString(),
            anyString(),
            any(List.class),
            any(InstructionDownloadCtrDestinationDTO.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));
    when(tenantSpecificConfigReader.getSorterContractVersion(any())).thenReturn(2);
    rdcOfflineReceiveService.autoReceiveContainersForOfflineReceiving(
        getLabelDataListForOfflineRcv(), instructionDownloadMessageDTO);
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), anyList());
  }

  @Test
  public void testAutoReceiveContainersForOfflineRcvSuccessWithChildForWpm() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());

    List<ReceivedContainer> receivedContainers = mockReceivedContainersWithChildForWpm();

    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("010840132899001809"));
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    InstructionDownloadBlobDataDTO instructionDownloadBlobDataDTO =
        new InstructionDownloadBlobDataDTO();
    instructionDownloadBlobDataDTO.setProjectedQty(12);
    instructionDownloadBlobDataDTO.setDeliveryNbr(2343427L);
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    deliveryDocument.setTrackingId("010840132899001809");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId("010840132899001809");
    deliveryDocument.getDeliveryDocumentLines().get(0).setChildTrackingId("010840132899909007");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcvWithChildForWPM();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    doReturn(receivedContainers.get(0))
        .when(rdcDaService)
        .buildReceivedContainer(
            any(LabelData.class),
            any(DeliveryDocument.class),
            any(DeliveryDocumentLine.class),
            anyString(),
            anyString(),
            any(List.class),
            any(InstructionDownloadCtrDestinationDTO.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());

    ArrayList wpmSites = new ArrayList();
    wpmSites.add("6014");
    when(rdcManagedConfig.getWpmSites()).thenReturn(wpmSites);
    when(rdcDaService.buildParentContainerForPutLabels(
            anyString(), any(DeliveryDocument.class), anyInt(), anyList()))
        .thenReturn(receivedContainers.get(1));
    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(1));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));
    when(tenantSpecificConfigReader.getSorterContractVersion(any())).thenReturn(1);
    rdcOfflineReceiveService.autoReceiveContainersForOfflineReceiving(
        getLabelDataListForOfflineRcvWpm(), instructionDownloadMessageDTO);
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), anyList());
  }

  @Test
  public void testAutoReceiveContainersForOfflineRcvSuccessWithChildForRdc2Rdc() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());

    List<ReceivedContainer> receivedContainers = mockReceivedContainersWithChildForWpm();

    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("010840132899001809"));
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    InstructionDownloadBlobDataDTO instructionDownloadBlobDataDTO =
        new InstructionDownloadBlobDataDTO();
    instructionDownloadBlobDataDTO.setProjectedQty(12);
    instructionDownloadBlobDataDTO.setDeliveryNbr(2343427L);
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    deliveryDocument.setTrackingId("010840132899001809");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId("010840132899001809");
    deliveryDocument.getDeliveryDocumentLines().get(0).setChildTrackingId("010840132899909007");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcvWithChildForWPM();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    doReturn(receivedContainers.get(0))
        .when(rdcDaService)
        .buildReceivedContainer(
            any(LabelData.class),
            any(DeliveryDocument.class),
            any(DeliveryDocumentLine.class),
            anyString(),
            anyString(),
            any(List.class),
            any(InstructionDownloadCtrDestinationDTO.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());

    ArrayList<String> rdc2rdcSites = new ArrayList();
    rdc2rdcSites.add("6014");
    when(rdcManagedConfig.getRdc2rdcSites()).thenReturn(rdc2rdcSites);
    when(rdcDaService.buildParentContainerForPutLabels(
            anyString(), any(DeliveryDocument.class), anyInt(), anyList()))
        .thenReturn(receivedContainers.get(1));
    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(1));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));
    rdcOfflineReceiveService.autoReceiveContainersForOfflineReceiving(
        getLabelDataListForOfflineRcvWpm(), instructionDownloadMessageDTO);
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), anyList());
  }

  @Test
  public void testAutoReceiveContainersForOfflineRcvSuccessWithChildForRdc2Rdc_false()
      throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());

    List<ReceivedContainer> receivedContainers = mockReceivedContainersWithChildForWpm();

    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("010840132899001809"));
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    InstructionDownloadBlobDataDTO instructionDownloadBlobDataDTO =
        new InstructionDownloadBlobDataDTO();
    instructionDownloadBlobDataDTO.setProjectedQty(12);
    instructionDownloadBlobDataDTO.setDeliveryNbr(2343427L);
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    deliveryDocument.setTrackingId("010840132899001809");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId("010840132899001809");
    deliveryDocument.getDeliveryDocumentLines().get(0).setChildTrackingId("010840132899909007");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcvWithChildForWPM();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    doReturn(receivedContainers.get(0))
        .when(rdcDaService)
        .buildReceivedContainer(
            any(LabelData.class),
            any(DeliveryDocument.class),
            any(DeliveryDocumentLine.class),
            anyString(),
            anyString(),
            any(List.class),
            any(InstructionDownloadCtrDestinationDTO.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());

    ArrayList<String> rdc2rdcSites = new ArrayList();
    rdc2rdcSites.add("1234");
    when(rdcManagedConfig.getRdc2rdcSites()).thenReturn(rdc2rdcSites);
    when(rdcDaService.buildParentContainerForPutLabels(
            anyString(), any(DeliveryDocument.class), anyInt(), anyList()))
        .thenReturn(receivedContainers.get(1));
    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(1));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));
    rdcOfflineReceiveService.autoReceiveContainersForOfflineReceiving(
        getLabelDataListForOfflineRcvWpm(), instructionDownloadMessageDTO);
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), anyList());
  }

  /**
   * This test function validates below use case Offline containers having pallet id i.e. coming
   * from AOS
   *
   * @throws Exception
   */
  @Test
  public void testAutoReceiveContainersForOfflineRcv_havingPallet() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());

    List<ReceivedContainer> receivedContainerForOffline = mockReceivedContainers();
    receivedContainerForOffline.forEach(
        receivedContainer -> receivedContainer.setPalletId("palletId"));

    String trackingId = "K107890123698769480000623";

    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("K107890123698769480000623"));
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId(trackingId);
    deliveryDocument.getDeliveryDocumentLines().get(0).setChildTrackingId(null);
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId(trackingId);
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcv();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    when(rdcManagedConfig.getEnableSingleTransactionForOffline()).thenReturn(true);

    when(rdcManagedConfig.getDcListEligibleForPrepareConsolidatedContainer())
        .thenReturn(Collections.EMPTY_LIST);
    when(rdcDaService.buildReceivedContainer(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            anyBoolean(),
            anyBoolean()))
        .thenReturn(receivedContainerForOffline.get(0));

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));

    ContainerItem containerItem = new ContainerItem();
    containerItem.setItemNumber(1232323L);
    containerItem.setTrackingId(trackingId);
    doReturn(containerItem)
        .when(rdcContainerUtils)
        .buildContainerItemDetails(any(), any(), any(), any(), any(), any(), any());

    doReturn(MockRdcInstruction.getContainer(trackingId))
        .when(rdcContainerUtils)
        .buildContainer(any(), any(), any(), any(), any(), any(), any(), any(), any());
    when(tenantSpecificConfigReader.getSorterContractVersion(any())).thenReturn(1);
    rdcOfflineReceiveService.autoReceiveContainersForOfflineReceiving(
        getLabelDataListForOfflineRcv_NoChildContainers(), instructionDownloadMessageDTO);
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), anyList());
    verify(labelDataService, times(1)).saveAll(any());
  }

  @Test
  public void testAutoReceiveContainersForOfflineRcvSuccess() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());

    List<ReceivedContainer> receivedContainerForOffline = mockReceivedContainers();

    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("K107890123698769480000623"));
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("K107890123698769480000623");
    deliveryDocument.getDeliveryDocumentLines().get(0).setChildTrackingId(null);
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId("K107890123698769480000623");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcv();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    when(rdcDaService.buildReceivedContainer(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            anyBoolean(),
            anyBoolean()))
        .thenReturn(receivedContainerForOffline.get(0));

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));
    when(tenantSpecificConfigReader.getSorterContractVersion(any())).thenReturn(2);
    rdcOfflineReceiveService.autoReceiveContainersForOfflineReceiving(
        getLabelDataListForOfflineRcv_NoChildContainers(), instructionDownloadMessageDTO);
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), anyList());
  }

  @Test
  public void testAutoReceiveContainersForOfflineRcvWithOutboxMode() throws Exception {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    HttpHeaders httpHeaders = new HttpHeaders();
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("E06938000020267142"));
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    InstructionDownloadBlobDataDTO instructionDownloadBlobDataDTO =
        new InstructionDownloadBlobDataDTO();
    instructionDownloadBlobDataDTO.setProjectedQty(12);
    instructionDownloadBlobDataDTO.setDeliveryNbr(2343427L);

    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId("343242132");
    receivedContainer.setStoreAlignment("MANUAL");
    receivedContainers.add(receivedContainer);

    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("343242132");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcv();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    boolean isAtlasConvertedItem = true;

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED,
            false))
        .thenReturn(true);

    rdcOfflineReceiveService.postReceivingUpdatesForOfflineRcv(
        instructionDownloadMessageDTO,
        isAtlasConvertedItem,
        receivedContainers,
        deliveryDocumentMap,
        new ArrayList<>());
  }

  @Test
  public void testPostReceivingUpdatesForOfflineRcv() throws Exception {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    HttpHeaders httpHeaders = new HttpHeaders();
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("E06938000020267142"));
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    InstructionDownloadBlobDataDTO instructionDownloadBlobDataDTO =
        new InstructionDownloadBlobDataDTO();
    instructionDownloadBlobDataDTO.setProjectedQty(12);
    instructionDownloadBlobDataDTO.setDeliveryNbr(2343427L);

    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId("343242132");
    receivedContainer.setStoreAlignment("MANUAL");
    receivedContainers.add(receivedContainer);

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("343242132");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcv();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    boolean isAtlasConvertedItem = true;
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    doNothing()
        .when(rdcContainerUtils)
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(true);
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            any(LabelAction.class),
            any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainerForSYMLabelType());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));

    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    rdcOfflineReceiveService.postReceivingUpdatesForOfflineRcv(
        instructionDownloadMessageDTO,
        isAtlasConvertedItem,
        receivedContainers,
        deliveryDocumentMap,
        new ArrayList<>());

    verify(rdcReceivingUtils, times(1)).persistOutboxEvents(anyCollection());
  }

  @Test
  public void testPostReceivingUpdatesForOfflineRcvWithOutboxMode() throws Exception {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    HttpHeaders httpHeaders = new HttpHeaders();
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("E06938000020267142"));
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    InstructionDownloadBlobDataDTO instructionDownloadBlobDataDTO =
        new InstructionDownloadBlobDataDTO();
    instructionDownloadBlobDataDTO.setProjectedQty(12);
    instructionDownloadBlobDataDTO.setDeliveryNbr(2343427L);

    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId("343242132");
    receivedContainer.setStoreAlignment("MANUAL");
    receivedContainers.add(receivedContainer);

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("343242132");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcv();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    boolean isAtlasConvertedItem = true;
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED,
            false))
        .thenReturn(true);
    doNothing()
        .when(rdcContainerUtils)
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(true);
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            any(LabelAction.class),
            any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainerForSYMLabelType());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));

    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    rdcOfflineReceiveService.postReceivingUpdatesForOfflineRcv(
        instructionDownloadMessageDTO,
        isAtlasConvertedItem,
        receivedContainers,
        deliveryDocumentMap,
        new ArrayList<>());

    verify(rdcReceivingUtils, times(1)).persistOutboxEvents(anyCollection());
  }

  @Test
  public void testPostReceivingUpdatesForOfflineRcvWithWFTPubSkip() throws Exception {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, "OFFLINE_RECEIVING");
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("E06938000020267142"));
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    InstructionDownloadBlobDataDTO instructionDownloadBlobDataDTO =
        new InstructionDownloadBlobDataDTO();
    instructionDownloadBlobDataDTO.setProjectedQty(12);
    instructionDownloadBlobDataDTO.setDeliveryNbr(2343427L);

    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId("343242132");
    receivedContainer.setStoreAlignment("MANUAL");
    receivedContainers.add(receivedContainer);

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("343242132");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcv();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    boolean isAtlasConvertedItem = true;
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    doNothing()
        .when(rdcContainerUtils)
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(true);
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            any(LabelAction.class),
            any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainerForSYMLabelType());
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));

    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    rdcOfflineReceiveService.postReceivingUpdatesForOfflineRcv(
        instructionDownloadMessageDTO,
        isAtlasConvertedItem,
        receivedContainers,
        deliveryDocumentMap,
        new ArrayList<>());

    verify(rdcReceivingUtils, times(1)).persistOutboxEvents(anyCollection());
  }

  @Test
  public void testTransformLabelDataForOfflineRcv() throws Exception {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, "OFFLINE_RECEIVING");
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("E06938000020267142"));
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    InstructionDownloadBlobDataDTO instructionDownloadBlobDataDTO =
        new InstructionDownloadBlobDataDTO();
    instructionDownloadBlobDataDTO.setProjectedQty(12);
    instructionDownloadBlobDataDTO.setDeliveryNbr(2343427L);

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    deliveryDocument.setTrackingId("E06938000020267142");
    deliveryDocument.getDeliveryDocumentLines().get(0).setChildTrackingId("5000000566385787");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId("E06938000020267142");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcv();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<LabelData> labelDataList = getLabelDataListForBuildContainer();
    labelDataList.get(0).setTrackingId("E06938000020267142");
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    List<ReceivedContainer> receivedContainers = mockReceivedContainersWithChild();
    ReceivedContainer parentReceivedContainer = mockReceivedContainers().get(0);
    boolean isAtlasConvertedItem = true;
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    doNothing()
        .when(rdcContainerUtils)
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(true);
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            any(LabelAction.class),
            any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainerForSYMLabelType());
    when(rdcDaService.buildReceivedContainer(
            any(LabelData.class),
            any(DeliveryDocument.class),
            any(DeliveryDocumentLine.class),
            anyString(),
            anyString(),
            any(List.class),
            any(InstructionDownloadCtrDestinationDTO.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean()))
        .thenReturn(receivedContainers.get(0));
    when(rdcDaService.buildParentContainerForPutLabels(
            anyString(), any(DeliveryDocument.class), anyInt(), any(List.class)))
        .thenReturn(receivedContainers.get(0));
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));

    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    rdcOfflineReceiveService.transformLabelDataForOfflineRcv(
        labelDataList, deliveryDocumentMap, httpHeaders, false);

    verify(rdcDaService, times(1))
        .buildParentContainerForPutLabels(
            anyString(), any(DeliveryDocument.class), anyInt(), any(List.class));
  }

  private static List<ReceivedContainer> mockReceivedContainers() {
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    ReceivedContainer receivedContainer1 = new ReceivedContainer();
    receivedContainer1.setLabelTrackingId("K107890123698769480000623");
    receivedContainer1.setStoreAlignment("MANUAL");
    receivedContainer1.setDeliveryNumber(123L);
    receivedContainer1.setParentTrackingId(null);
    receivedContainer1.setPack(4);
    receivedContainers.add(receivedContainer1);
    ReceivedContainer receivedContainer2 = new ReceivedContainer();
    receivedContainer2.setLabelTrackingId("E06938000020267142");
    receivedContainer2.setStoreAlignment("MANUAL");
    receivedContainer2.setParentTrackingId(null);
    receivedContainer2.setDeliveryNumber(123L);
    receivedContainer2.setPack(4);
    receivedContainers.add(receivedContainer2);
    return receivedContainers;
  }

  private static List<ReceivedContainer> mockReceivedContainersWithChild() {
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    ReceivedContainer receivedContainer1 = new ReceivedContainer();
    receivedContainer1.setLabelTrackingId("343242132");
    receivedContainer1.setStoreAlignment("MANUAL");
    receivedContainer1.setParentTrackingId("E06938000020267142");
    receivedContainer1.setPack(4);
    ReceivedContainer receivedContainer2 = new ReceivedContainer();
    receivedContainer2.setLabelTrackingId("E06938000020267142");
    receivedContainer2.setStoreAlignment("MANUAL");
    receivedContainer2.setParentTrackingId(null);
    receivedContainer2.setPack(4);
    receivedContainers.add(receivedContainer2);
    return receivedContainers;
  }

  private static List<ReceivedContainer> mockReceivedContainersWithChildForWpm() {
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    ReceivedContainer receivedContainer1 = new ReceivedContainer();
    receivedContainer1.setLabelTrackingId("010840132899909007");
    receivedContainer1.setStoreAlignment("MANUAL");
    receivedContainer1.setParentTrackingId("010840132899001809");
    receivedContainer1.setPack(4);
    receivedContainers.add(receivedContainer1);
    ReceivedContainer receivedContainer2 = new ReceivedContainer();
    receivedContainer2.setLabelTrackingId("010840132899001809");
    receivedContainer2.setStoreAlignment("MANUAL");
    receivedContainer2.setParentTrackingId(null);
    receivedContainer2.setPack(4);
    receivedContainers.add(receivedContainer2);
    return receivedContainers;
  }

  private byte[] getInstructionsDownloadDataForOfflineRcv() {
    byte data[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_FOR_OFFLINE_RCV.getBytes();
    return data;
  }

  private byte[] getInstructionsDownloadDataForOfflineRcvWithChild() {
    byte data[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_FOR_OFFLINE_RCV_WITH_CHILD
            .getBytes();
    return data;
  }

  private byte[] getInstructionsDownloadDataForOfflineRcvWithChildForWPM() {
    byte data[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_FOR_OFFLINE_RCV_WPM.getBytes();
    return data;
  }

  private List<LabelData> getLabelDataListForOfflineRcv() throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    List<LabelData> labelDataList =
        objectMapper.readValue(
            MockInstructionDownloadEvent.LABEL_DATA_LIST_FOR_OFFLINE_RCV,
            new TypeReference<List<LabelData>>() {});
    return labelDataList;
  }

  private List<LabelData> getLabelDataListForOfflineRcvWpm() throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    List<LabelData> labelDataList =
        objectMapper.readValue(
            MockInstructionDownloadEvent.LABEL_DATA_LIST_FOR_OFFLINE_RCV_FOR_WPM,
            new TypeReference<List<LabelData>>() {});
    return labelDataList;
  }

  private List<LabelData> getLabelDataListForOfflineRcv_NoChildContainers()
      throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    List<LabelData> labelDataList =
        objectMapper.readValue(
            MockInstructionDownloadEvent.LABEL_DATA_LIST_FOR_OFFLINE_RCV_WITHOUT_CHILD,
            new TypeReference<List<LabelData>>() {});
    return labelDataList;
  }

  private Container getMockContainerForSYMLabelType() {
    Container container = new Container();
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "02323");
    destination.put(ReceivingConstants.COUNTRY_CODE, "US");
    container.setDeliveryNumber(123L);
    container.setInstructionId(12345L);
    container.setTrackingId("r2323232308969587");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    container.setDestination(destination);
    container.setInventoryStatus(InventoryStatus.ALLOCATED.name());
    return container;
  }

  private List<LabelData> getLabelDataListForBuildContainer() throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    List<LabelData> labelDataList =
        objectMapper.readValue(
            MockInstructionDownloadEvent.LABEL_DATA_LIST_FOR_BUILD_CONTAINER,
            new TypeReference<List<LabelData>>() {});
    return labelDataList;
  }

  @Test
  public void testAutoReceiveContainersForOfflineReceiving() throws Exception {

    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, "OFFLINE_RECEIVING");
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("E06938000020267142"));

    InstructionDownloadBlobDataDTO instructionDownloadBlobDataDTO =
        new InstructionDownloadBlobDataDTO();
    instructionDownloadBlobDataDTO.setProjectedQty(12);
    instructionDownloadBlobDataDTO.setDeliveryNbr(2343427L);

    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    deliveryDocument.setTrackingId("E06938000020267142");
    deliveryDocument.getDeliveryDocumentLines().get(0).setChildTrackingId("5000000566385787");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId("E06938000020267142");

    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);

    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcv();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());

    List<LabelData> labelDataList = getLabelDataListForBuildContainer();
    labelDataList.get(0).setTrackingId("E06938000020267142");
    labelDataList
        .get(0)
        .getAllocation()
        .getChildContainers()
        .get(0)
        .setTrackingId("E06938000020267142");
    //    labelDataList.get(0).getAllocation().setChildContainers(Collections.emptyList());

    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());
    instructionDownloadBlobDataDTOList.get(0).getContainer().setTrackingId("E06938000020267142");
    instructionDownloadBlobDataDTOList.get(0).setChildContainers(Collections.emptyList());
    instructionDownloadBlobDataDTOList.get(0).setProjectedQty(1);

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    List<ReceivedContainer> receivedContainers = mockReceivedContainersWithChild();
    receivedContainers.get(0).setParentTrackingId(null);
    receivedContainers.get(0).setLabelTrackingId("E06938000020267142");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    doNothing()
        .when(rdcContainerUtils)
        .postReceiptsToDcFin(any(Container.class), any(String.class));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(true);
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            any(LabelAction.class),
            any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(getMockContainerForSYMLabelType());
    when(rdcDaService.buildReceivedContainer(
            any(LabelData.class),
            any(DeliveryDocument.class),
            any(DeliveryDocumentLine.class),
            anyString(),
            anyString(),
            any(List.class),
            any(Facility.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean()))
        .thenReturn(receivedContainers.get(0));
    when(rdcDaService.buildParentContainerForPutLabels(
            anyString(), any(DeliveryDocument.class), anyInt(), any(List.class)))
        .thenReturn(receivedContainers.get(0));
    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));

    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));

    when(rdcManagedConfig.getEnableSingleTransactionForOffline()).thenReturn(false);

    rdcOfflineReceiveService.autoReceiveContainersForOfflineReceiving(
        labelDataList, instructionDownloadMessageDTO);

    verify(rdcDaService, times(1))
        .buildParentContainerForPutLabels(
            anyString(), any(DeliveryDocument.class), anyInt(), any(List.class));

    verify(rdcDaService, times(1))
        .buildReceivedContainer(
            any(LabelData.class),
            any(DeliveryDocument.class),
            any(DeliveryDocumentLine.class),
            anyString(),
            anyString(),
            any(List.class),
            any(Facility.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
  }

  @Test
  public void testTransformLabelDataForOfflineRcvWithEmptyChild() throws Exception {

    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, "OFFLINE_RECEIVING");
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("E06938000020267142"));

    InstructionDownloadBlobDataDTO instructionDownloadBlobDataDTO =
        new InstructionDownloadBlobDataDTO();
    instructionDownloadBlobDataDTO.setProjectedQty(12);
    instructionDownloadBlobDataDTO.setDeliveryNbr(2343427L);

    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    deliveryDocument.setTrackingId("E06938000020267142");
    deliveryDocument.getDeliveryDocumentLines().get(0).setChildTrackingId("5000000566385787");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId("E06938000020267142");

    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);

    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcv();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());

    List<LabelData> labelDataList = getLabelDataListForBuildContainer();
    labelDataList.get(0).setTrackingId("E06938000020267142");
    labelDataList.get(0).getAllocation().setChildContainers(Collections.emptyList());

    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());
    instructionDownloadBlobDataDTOList.get(0).getContainer().setTrackingId("E06938000020267142");
    instructionDownloadBlobDataDTOList.get(0).setChildContainers(Collections.emptyList());
    instructionDownloadBlobDataDTOList.get(0).setProjectedQty(1);

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    List<ReceivedContainer> receivedContainers = mockReceivedContainersWithChild();
    receivedContainers.get(0).setParentTrackingId(null);
    receivedContainers.get(0).setLabelTrackingId("E06938000020267142");
    when(rdcDaService.buildReceivedContainer(
            any(LabelData.class),
            any(DeliveryDocument.class),
            any(DeliveryDocumentLine.class),
            anyString(),
            any(),
            any(List.class),
            any(Facility.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean()))
        .thenReturn(receivedContainers.get(0));
    rdcOfflineReceiveService.transformLabelDataForOfflineRcv(
        labelDataList, deliveryDocumentMap, httpHeaders, false);

    verify(rdcDaService, times(1))
        .buildReceivedContainer(
            any(LabelData.class),
            any(DeliveryDocument.class),
            any(DeliveryDocumentLine.class),
            anyString(),
            any(),
            any(List.class),
            any(Facility.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
  }

  @Test
  public void testPostReceivingUpdatesForOfflineRcvWithChildContainers() throws Exception {

    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, "OFFLINE_RECEIVING");
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("E06938000020267142"));

    InstructionDownloadBlobDataDTO instructionDownloadBlobDataDTO =
        new InstructionDownloadBlobDataDTO();
    instructionDownloadBlobDataDTO.setProjectedQty(12);
    instructionDownloadBlobDataDTO.setDeliveryNbr(2343427L);

    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    deliveryDocument.setTrackingId("E06938000020267142");
    deliveryDocument.getDeliveryDocumentLines().get(0).setChildTrackingId("5000000566385787");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId("E06938000020267142");

    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);

    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcv();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());

    List<LabelData> labelDataList = getLabelDataListForBuildContainer();
    labelDataList.get(0).setTrackingId("E06938000020267142");
    //    labelDataList.get(0).getAllocation().setChildContainers(Collections.emptyList());

    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());
    instructionDownloadBlobDataDTOList.get(0).getContainer().setTrackingId("E06938000020267142");
    List<InstructionDownloadChildContainerDTO> childContainers = new ArrayList<>();
    InstructionDownloadChildContainerDTO instructionDownloadChildContainerDTO =
        new InstructionDownloadChildContainerDTO();

    List<InstructionDownloadDistributionsDTO> distributions = new ArrayList<>();
    InstructionDownloadDistributionsDTO instructionDownloadDistributionsDTO =
        new InstructionDownloadDistributionsDTO();
    InstructionDownloadItemDTO instructionDownloadItemDTO = new InstructionDownloadItemDTO();
    instructionDownloadItemDTO.setItemNbr(168l);

    instructionDownloadDistributionsDTO.setItem(instructionDownloadItemDTO);
    distributions.add(instructionDownloadDistributionsDTO);

    instructionDownloadChildContainerDTO.setDistributions(distributions);
    childContainers.add(instructionDownloadChildContainerDTO);
    instructionDownloadBlobDataDTOList.get(0).setChildContainers(childContainers);
    instructionDownloadBlobDataDTOList.get(0).setProjectedQty(1);

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    List<ReceivedContainer> receivedContainers = mockReceivedContainersWithChild();
    receivedContainers.get(0).setParentTrackingId(null);
    receivedContainers.get(0).setLabelTrackingId("E06938000020267142");

    rdcOfflineReceiveService.postReceivingUpdatesForOfflineRcv(
        instructionDownloadMessageDTO,
        true,
        receivedContainers,
        deliveryDocumentMap,
        new ArrayList<>());
  }

  /**
   * This test validates autoReceiveContainersForOfflineReceiving() when
   * enablePrepareConsolidatedContainers flag is true and dc not matching
   *
   * @throws Exception
   */
  @Test
  public void testAutoReceiveContainersForOfflineRcv_enable_CC_flag_dc_mismatch() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());

    List<ReceivedContainer> receivedContainerForOffline = mockReceivedContainers();

    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("K107890123698769480000623"));
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("K107890123698769480000623");
    deliveryDocument.getDeliveryDocumentLines().get(0).setChildTrackingId(null);
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId("K107890123698769480000623");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcv();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    when(rdcManagedConfig.getEnableSingleTransactionForOffline()).thenReturn(true);

    when(rdcManagedConfig.getDcListEligibleForPrepareConsolidatedContainer())
        .thenReturn(Arrays.asList("32899"));

    when(rdcDaService.buildReceivedContainer(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            anyBoolean(),
            anyBoolean()))
        .thenReturn(receivedContainerForOffline.get(0));

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));
    rdcOfflineReceiveService.autoReceiveContainersForOfflineReceiving(
        getLabelDataListForOfflineRcv_NoChildContainers(), instructionDownloadMessageDTO);
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), anyList());
  }

  /**
   * This test validates autoReceiveContainersForOfflineReceiving() when
   * enablePrepareConsolidatedContainers flag is true and All dc Eligible
   *
   * @throws Exception
   */
  @Test
  public void testAutoReceiveContainersForOfflineRcv_enable_CC_flag_all_dc_eligible()
      throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());

    List<ReceivedContainer> receivedContainerForOffline = mockReceivedContainers();

    String trackingId = "K107890123698769480000623";

    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("K107890123698769480000623"));
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId(trackingId);
    deliveryDocument.getDeliveryDocumentLines().get(0).setChildTrackingId(null);
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId(trackingId);
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcv();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    when(rdcManagedConfig.getEnableSingleTransactionForOffline()).thenReturn(true);

    when(rdcManagedConfig.getDcListEligibleForPrepareConsolidatedContainer())
        .thenReturn(Collections.EMPTY_LIST);
    when(rdcDaService.buildReceivedContainer(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            anyBoolean(),
            anyBoolean()))
        .thenReturn(receivedContainerForOffline.get(0));

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));

    ContainerItem containerItem = new ContainerItem();
    containerItem.setItemNumber(1232323L);
    containerItem.setTrackingId(trackingId);
    doReturn(containerItem)
        .when(rdcContainerUtils)
        .buildContainerItemDetails(any(), any(), any(), any(), any(), any(), any());

    doReturn(MockRdcInstruction.getContainer(trackingId))
        .when(rdcContainerUtils)
        .buildContainer(any(), any(), any(), any(), any(), any(), any(), any(), any());

    rdcOfflineReceiveService.autoReceiveContainersForOfflineReceiving(
        getLabelDataListForOfflineRcv_NoChildContainers(), instructionDownloadMessageDTO);
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), anyList());
    verify(labelDataService, times(1)).saveAll(any());
  }

  /**
   * This test validates autoReceiveContainersForOfflineReceiving() when
   * enablePrepareConsolidatedContainers flag is true and dc matching
   *
   * @throws Exception
   */
  @Test
  public void testAutoReceiveContainersForOfflineRcv_enable_CC_flag_dc_matching() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());

    List<ReceivedContainer> receivedContainerForOffline = mockReceivedContainers();

    String trackingId = "K107890123698769480000623";

    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("K107890123698769480000623"));
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId(trackingId);
    deliveryDocument.getDeliveryDocumentLines().get(0).setChildTrackingId(null);
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId(trackingId);
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcv();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    when(rdcManagedConfig.getEnableSingleTransactionForOffline()).thenReturn(true);

    when(rdcManagedConfig.getDcListEligibleForPrepareConsolidatedContainer())
        .thenReturn(Arrays.asList("6014"));
    when(rdcDaService.buildReceivedContainer(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            anyBoolean(),
            anyBoolean()))
        .thenReturn(receivedContainerForOffline.get(0));

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));

    ContainerItem containerItem = new ContainerItem();
    containerItem.setItemNumber(1232323L);
    containerItem.setTrackingId(trackingId);
    doReturn(containerItem)
        .when(rdcContainerUtils)
        .buildContainerItemDetails(any(), any(), any(), any(), any(), any(), any());

    doReturn(MockRdcInstruction.getContainer(trackingId))
        .when(rdcContainerUtils)
        .buildContainer(any(), any(), any(), any(), any(), any(), any(), any(), any());

    rdcOfflineReceiveService.autoReceiveContainersForOfflineReceiving(
        getLabelDataListForOfflineRcv_NoChildContainers(), instructionDownloadMessageDTO);
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), anyList());
    verify(labelDataService, times(1)).saveAll(any());
  }

  /**
   * This test validates autoReceiveContainersForOfflineReceiving() with child Container when
   * enablePrepareConsolidatedContainers flag is true and eligible All DC
   *
   * @throws Exception
   */
  @Test
  public void testAutoReceiveContainersForOfflineRcvSuccessWithChild_enable_CC() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());

    String trackingId = "E06938000020267142";

    List<ReceivedContainer> receivedContainers = mockReceivedContainersWithChild();

    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        gson.fromJson(
            MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
            InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList(trackingId));
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    InstructionDownloadBlobDataDTO instructionDownloadBlobDataDTO =
        new InstructionDownloadBlobDataDTO();
    instructionDownloadBlobDataDTO.setProjectedQty(12);
    instructionDownloadBlobDataDTO.setDeliveryNbr(2343427L);
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    deliveryDocument.setTrackingId(trackingId);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .setChildTrackingId("a32L8990000000000000106520");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId(trackingId);
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcvWithChild();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    doReturn(receivedContainers.get(0))
        .when(rdcDaService)
        .buildReceivedContainer(
            any(LabelData.class),
            any(DeliveryDocument.class),
            any(DeliveryDocumentLine.class),
            anyString(),
            anyString(),
            any(List.class),
            any(InstructionDownloadCtrDestinationDTO.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());

    ContainerItem containerItem = new ContainerItem();
    containerItem.setItemNumber(1232323L);
    containerItem.setTrackingId(trackingId);
    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setItemNumber(1232323L);
    containerItem2.setTrackingId("a32L8990000000000000106520");
    doReturn(containerItem, containerItem2)
        .when(rdcContainerUtils)
        .buildContainerItemDetails(any(), any(), any(), any(), any(), any(), any());

    doReturn(
            MockRdcInstruction.getContainer(trackingId),
            MockRdcInstruction.getChildContainer(trackingId))
        .when(rdcContainerUtils)
        .buildContainer(any(), any(), any(), any(), any(), any(), any(), any(), any());

    when(rdcManagedConfig.getEnableSingleTransactionForOffline()).thenReturn(true);

    when(rdcManagedConfig.getDcListEligibleForPrepareConsolidatedContainer())
        .thenReturn(Collections.EMPTY_LIST);

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));
    rdcOfflineReceiveService.autoReceiveContainersForOfflineReceiving(
        getLabelDataListForOfflineRcv(), instructionDownloadMessageDTO);
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(any(), any(), any(), any(), anyList());
  }
}
