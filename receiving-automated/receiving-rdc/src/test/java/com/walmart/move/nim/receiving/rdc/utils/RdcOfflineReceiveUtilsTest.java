package com.walmart.move.nim.receiving.rdc.utils;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertNotNull;
import static org.testng.AssertJUnit.assertEquals;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.EventType;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.SymboticPutawayPublishHelper;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.publisher.JMSSorterPublisher;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.instruction.*;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockInstructionDownloadEvent;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdcInstruction;
import com.walmart.move.nim.receiving.rdc.service.RdcInstructionHelper;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.repositories.OutboxEvent;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** Unit test cases for RdcOfflineReceiveService class */
public class RdcOfflineReceiveUtilsTest {
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RdcContainerUtils rdcContainerUtils;
  @Mock private ReceiptService receiptService;
  @Mock private RdcInstructionHelper rdcInstructionHelper;
  @Mock private RdcReceivingUtils rdcReceivingUtils;
  @Mock private LabelDataService labelDataService;
  @InjectMocks private RdcOfflineRecieveUtils rdcOfflineRecieveUtils;
  @Mock AppConfig appConfig;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private InstructionHelperService instructionHelperService;
  @Mock private JMSSorterPublisher jmsSorterPublisher;
  @Mock private SymboticPutawayPublishHelper symboticPutawayPublishHelper;

  private HttpHeaders httpHeaders;
  private String facilityNum = "32818";
  private String facilityCountryCode = "us";
  private Gson gson;

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
  }

  @AfterMethod
  public void tearDown() {
    reset(
        rdcContainerUtils,
        receiptService,
        rdcInstructionHelper,
        rdcReceivingUtils,
        labelDataService,
        appConfig);
  }

  @Test
  public void testBuildOutboxEventsForOffline() throws Exception {
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

    List<ReceivedContainer> receivedContainers = getReceivedContainers();

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("343242132");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList =
        getInstructionDownloadBlobDataDTOS();

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);

    // Define the behavior of mocked dependencies
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(new Container());

    // Run the method under test
    Collection<OutboxEvent> outboxEvents =
        rdcOfflineRecieveUtils.buildOutboxEventsForOffline(
            receivedContainers, httpHeaders, instruction, deliveryDocumentMap, new ArrayList<>());

    // Verify the result
    assertNotNull(outboxEvents);
  }

  @Test
  public void testPostReceivingUpdatesForOffline() throws ReceivingException, IOException {
    // Prepare test data
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

    List<ReceivedContainer> receivedContainers = getReceivedContainers();

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("343242132");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList =
        getInstructionDownloadBlobDataDTOS();

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);

    // Mock methods
    Container consolidatedContainer = new Container();
    consolidatedContainer.setAsnNumber("asnNbr");
    consolidatedContainer.setTrackingId("trackingId");
    consolidatedContainer.setInventoryStatus("invStatus");
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(consolidatedContainer);

    rdcOfflineRecieveUtils.postReceivingUpdatesForOffline(
        instruction, deliveryDocumentMap, httpHeaders, true, receivedContainers, new ArrayList<>());

    // Verify methods were called
    //        verify(containerPersisterService,
    // times(1)).getConsolidatedContainerForPublish(anyString());
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
  }

  @Test
  public void testPostReceivingUpdatesForOfflineSorter_XDK1()
      throws ReceivingException, IOException {
    // Prepare test data
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
    receivedContainer.setLabelType("XDK1");
    receivedContainer.setSorterDivertRequired(true);
    receivedContainer.setAsnNumber("1234");
    receivedContainers.add(receivedContainer);

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("343242132");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData additionalInfo = new ItemData();
    additionalInfo.setItemHandlingMethod("I");
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList =
        getInstructionDownloadBlobDataDTOS();

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);

    // Mock methods
    Container consolidatedContainer = new Container();
    consolidatedContainer.setAsnNumber("asnNbr");
    consolidatedContainer.setTrackingId("trackingId");
    consolidatedContainer.setInventoryStatus("invStatus");
    Map<String, String> destination = new HashMap<>();
    destination.put("buNumber", "buNumber");
    consolidatedContainer.setDestination(destination);
    List<String> eligibleHandlingCodes = new ArrayList<>();
    eligibleHandlingCodes.add("I");
    when(rdcManagedConfig.getOfflineEligibleItemHandlingCodes()).thenReturn(eligibleHandlingCodes);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(consolidatedContainer);
    when(rdcManagedConfig.getAtlasNonConveyableHandlingCodesForDaSlotting())
        .thenReturn(Collections.singletonList("L"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);

    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    rdcOfflineRecieveUtils.postReceivingUpdatesForOffline(
        instruction, deliveryDocumentMap, httpHeaders, true, receivedContainers, new ArrayList<>());

    // Verify methods were called
    //        verify(containerPersisterService,
    // times(1)).getConsolidatedContainerForPublish(anyString());
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
  }

  @Test
  public void testPostReceivingUpdatesForOfflineSorter_XDK2()
      throws ReceivingException, IOException {
    // Prepare test data
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
    receivedContainer.setLabelType("XDK2");
    receivedContainer.setSorterDivertRequired(true);
    receivedContainers.add(receivedContainer);

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("343242132");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData additionalInfo = new ItemData();
    additionalInfo.setItemHandlingMethod("I");
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList =
        getInstructionDownloadBlobDataDTOS();

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);

    // Mock methods
    Container consolidatedContainer = new Container();
    consolidatedContainer.setAsnNumber("asnNbr");
    consolidatedContainer.setTrackingId("trackingId");
    consolidatedContainer.setInventoryStatus("invStatus");
    Map<String, String> destination = new HashMap<>();
    destination.put("buNumber", "buNumber");
    consolidatedContainer.setDestination(destination);
    List<String> eligibleHandlingCodes = new ArrayList<>();
    eligibleHandlingCodes.add("I");
    when(rdcManagedConfig.getOfflineEligibleItemHandlingCodes()).thenReturn(eligibleHandlingCodes);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(consolidatedContainer);
    when(rdcManagedConfig.getAtlasNonConveyableHandlingCodesForDaSlotting())
        .thenReturn(Collections.singletonList("L"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());
    //        when(symboticPutawayPublishHelper.publishPutawayAddMessage(receivedContainer,
    // deliveryDocument, instruction, any(), httpHeaders)).thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);

    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    rdcOfflineRecieveUtils.postReceivingUpdatesForOffline(
        instruction, deliveryDocumentMap, httpHeaders, true, receivedContainers, new ArrayList<>());

    // Verify methods were called
    //        verify(containerPersisterService,
    // times(1)).getConsolidatedContainerForPublish(anyString());
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
  }

  @Test
  public void testPostReceivingUpdatesForOfflineSorter_XDK2_SYM()
      throws ReceivingException, IOException {
    // Prepare test data
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
    receivedContainer.setStoreAlignment("SYM2");
    receivedContainer.setLabelType("XDK2");
    receivedContainer.setSorterDivertRequired(true);
    receivedContainers.add(receivedContainer);

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("343242132");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData additionalInfo = new ItemData();
    additionalInfo.setItemHandlingMethod("I");
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList =
        getInstructionDownloadBlobDataDTOS();

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);

    Container consolidatedContainer = new Container();
    consolidatedContainer.setAsnNumber("asnNbr");
    consolidatedContainer.setTrackingId("trackingId");
    consolidatedContainer.setInventoryStatus("invStatus");
    Map<String, String> destination = new HashMap<>();
    destination.put("buNumber", "buNumber");
    consolidatedContainer.setDestination(destination);
    List<String> eligibleHandlingCodes = new ArrayList<>();
    eligibleHandlingCodes.add("I");
    when(rdcManagedConfig.getOfflineEligibleItemHandlingCodes()).thenReturn(eligibleHandlingCodes);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(consolidatedContainer);
    when(rdcManagedConfig.getAtlasNonConveyableHandlingCodesForDaSlotting())
        .thenReturn(Collections.singletonList("L"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);

    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    rdcOfflineRecieveUtils.postReceivingUpdatesForOffline(
        instruction, deliveryDocumentMap, httpHeaders, true, receivedContainers, new ArrayList<>());
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
  }

  @Test
  public void testPostReceivingUpdatesForOfflineSorter_WPM()
      throws ReceivingException, IOException {
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
    receivedContainer.setLabelType("XDK2");
    receivedContainer.setSorterDivertRequired(true);
    receivedContainers.add(receivedContainer);

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("343242132");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    deliveryDocument.setOriginFacilityNum(6014);
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData additionalInfo = new ItemData();
    additionalInfo.setItemHandlingMethod("I");
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcvForWpm();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);

    Container consolidatedContainer = new Container();
    consolidatedContainer.setAsnNumber("asnNbr");
    consolidatedContainer.setTrackingId("trackingId");
    consolidatedContainer.setInventoryStatus("PICKED");
    consolidatedContainer.setContainerType("REPACK");
    Map<String, String> destination = new HashMap<>();
    destination.put("buNumber", "buNumber");
    consolidatedContainer.setDestination(destination);
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(ReceivingConstants.ORIGIN_FACILITY_NUMBER, "6014");
    consolidatedContainer.setContainerMiscInfo(containerMiscInfo);
    List<String> eligibleHandlingCodes = new ArrayList<>();
    eligibleHandlingCodes.add("I");
    when(rdcManagedConfig.getOfflineEligibleItemHandlingCodes()).thenReturn(eligibleHandlingCodes);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(consolidatedContainer);
    ArrayList wpmSites = new ArrayList<>();
    wpmSites.add("6014");

    when(rdcManagedConfig.getWpmSites()).thenReturn(wpmSites);
    when(rdcManagedConfig.getAtlasNonConveyableHandlingCodesForDaSlotting())
        .thenReturn(Collections.singletonList("L"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);

    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    rdcOfflineRecieveUtils.postReceivingUpdatesForOffline(
        instruction, deliveryDocumentMap, httpHeaders, true, receivedContainers, new ArrayList<>());

    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
  }

  @Test
  public void testPostReceivingUpdatesForOfflineSorter_WPM_VendorPack()
      throws ReceivingException, IOException {
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
    receivedContainer.setStoreAlignment("SYM2");
    receivedContainer.setLabelType("XDK2");
    receivedContainer.setSorterDivertRequired(true);
    receivedContainers.add(receivedContainer);

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("343242132");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData additionalInfo = new ItemData();
    additionalInfo.setItemHandlingMethod("I");
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    byte blobData[] = getInstructionsDownloadDataForOfflineRcvForWpm();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);

    Container consolidatedContainer = new Container();
    consolidatedContainer.setAsnNumber("asnNbr");
    consolidatedContainer.setTrackingId("trackingId");
    consolidatedContainer.setInventoryStatus("invStatus");
    consolidatedContainer.setContainerType("Vendor Pack");
    Map<String, String> destination = new HashMap<>();
    destination.put("buNumber", "buNumber");
    consolidatedContainer.setDestination(destination);
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(ReceivingConstants.ORIGIN_FACILITY_NUMBER, "6014");
    consolidatedContainer.setContainerMiscInfo(containerMiscInfo);
    List<String> eligibleHandlingCodes = new ArrayList<>();
    eligibleHandlingCodes.add("I");
    when(rdcManagedConfig.getOfflineEligibleItemHandlingCodes()).thenReturn(eligibleHandlingCodes);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(consolidatedContainer);
    ArrayList wpmSites = new ArrayList<>();
    wpmSites.add("6014");

    when(rdcManagedConfig.getWpmSites()).thenReturn(wpmSites);
    when(rdcManagedConfig.getAtlasNonConveyableHandlingCodesForDaSlotting())
        .thenReturn(Collections.singletonList("L"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_JMS,
            false))
        .thenReturn(true);
    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);

    doNothing().when(jmsSorterPublisher).publishLabelToSorter(any(Container.class), anyString());

    rdcOfflineRecieveUtils.postReceivingUpdatesForOffline(
        instruction, deliveryDocumentMap, httpHeaders, true, receivedContainers, new ArrayList<>());

    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
  }

  @Test
  public void testisOfflineWpmContainer() throws ReceivingException, IOException {
    Container container = new Container();
    Map<String, Object> misInfo = new HashMap<>();
    container.setContainerMiscInfo(misInfo);
    Boolean isOfflineContainer = rdcOfflineRecieveUtils.isOfflineWpmContainer(container);
    assertEquals(isOfflineContainer, Boolean.FALSE);
  }

  @Test
  public void testisOfflineWpmContainer_notContainsOriginDCKey()
      throws ReceivingException, IOException {
    Container container = new Container();
    Map<String, Object> misInfo = new HashMap<>();
    misInfo.put("labelType", "XDK1");
    container.setContainerMiscInfo(misInfo);
    Boolean isOfflineContainer = rdcOfflineRecieveUtils.isOfflineWpmContainer(container);
    assertEquals(isOfflineContainer, Boolean.FALSE);
  }

  @Test
  public void testisOfflineWpmContainer_nullOriginDCKey() throws ReceivingException, IOException {
    Container container = new Container();
    Map<String, Object> misInfo = new HashMap<>();
    misInfo.put("labelType", "XDK1");
    misInfo.put("originFacilityNum", null);
    container.setContainerMiscInfo(misInfo);
    Boolean isOfflineContainer = rdcOfflineRecieveUtils.isOfflineWpmContainer(container);
    assertEquals(isOfflineContainer, Boolean.FALSE);
  }

  @Test
  public void testisOfflineRdc2RdcContainer() throws ReceivingException, IOException {
    Container container = new Container();
    Map<String, Object> misInfo = new HashMap<>();
    misInfo.put("labelType", "XDK1");
    misInfo.put("originFacilityNum", "6014");
    container.setContainerMiscInfo(misInfo);
    when(rdcManagedConfig.getRdc2rdcSites()).thenReturn(Arrays.asList("6014"));
    Boolean isOfflineContainer = rdcOfflineRecieveUtils.isOfflineWpmContainer(container);
    assertEquals(isOfflineContainer, Boolean.TRUE);
  }

  @Test
  public void testisOfflineRdc2RdcContainer_false() throws ReceivingException, IOException {
    Container container = new Container();
    Map<String, Object> misInfo = new HashMap<>();
    misInfo.put("labelType", "XDK1");
    misInfo.put("originFacilityNum", "1234");
    container.setContainerMiscInfo(misInfo);
    when(rdcManagedConfig.getRdc2rdcSites()).thenReturn(Arrays.asList("6014"));
    Boolean isOfflineContainer = rdcOfflineRecieveUtils.isOfflineWpmContainer(container);
    assertEquals(isOfflineContainer, Boolean.FALSE);
  }

  @Test
  public void testisOfflineWpmContainer_OriginDCKey() throws ReceivingException, IOException {
    Container container = new Container();
    Map<String, Object> misInfo = new HashMap<>();
    misInfo.put("labelType", "XDK1");
    misInfo.put("originFacilityNum", 6014);
    container.setContainerMiscInfo(misInfo);
    when(rdcManagedConfig.getWpmSites()).thenReturn(Arrays.asList("6014"));
    Boolean isOfflineContainer = rdcOfflineRecieveUtils.isOfflineWpmContainer(container);
    assertEquals(isOfflineContainer, Boolean.TRUE);
  }

  private byte[] getInstructionsDownloadDataForOfflineRcv() {
    byte data[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_FOR_OFFLINE_RCV.getBytes();
    return data;
  }

  private byte[] getInstructionsDownloadDataForOfflineRcvForWpm() {
    byte data[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_FOR_OFFLINE_RCV_WPM.getBytes();
    return data;
  }

  /**
   * This test validates postReceivingUpdatesForOffline() when enablePrepareConsolidatedContainers
   * flag is true
   *
   * @throws ReceivingException
   * @throws IOException
   */
  @Test
  public void testPostReceivingUpdatesForOffline_enable_CC_flag_true()
      throws ReceivingException, IOException {
    // Prepare test data
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

    List<ReceivedContainer> receivedContainers = getReceivedContainers();

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("343242132");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList =
        getInstructionDownloadBlobDataDTOS();

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);

    // Mock methods
    Container consolidatedContainer = new Container();
    consolidatedContainer.setAsnNumber("asnNbr");
    consolidatedContainer.setTrackingId("trackingId");
    consolidatedContainer.setInventoryStatus("invStatus");
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(consolidatedContainer);
    when(rdcManagedConfig.getEnableSingleTransactionForOffline()).thenReturn(true);

    rdcOfflineRecieveUtils.postReceivingUpdatesForOffline(
        instruction, deliveryDocumentMap, httpHeaders, true, receivedContainers, new ArrayList<>());
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
  }

  /**
   * Build received container
   *
   * @return
   */
  private static List<ReceivedContainer> getReceivedContainers() {
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId("343242132");
    receivedContainer.setStoreAlignment("MANUAL");
    receivedContainer.setAsnNumber("1234");
    receivedContainers.add(receivedContainer);
    return receivedContainers;
  }

  /**
   * This test validates postReceivingUpdatesForOffline() when enablePrepareConsolidatedContainers
   * flag is true but consolidatedContainerList is empty
   *
   * @throws ReceivingException
   * @throws IOException
   */
  @Test
  public void testPostReceivingUpdatesForOffline_consolidatedContainerList_empty()
      throws ReceivingException, IOException {
    // Prepare test data
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

    List<ReceivedContainer> receivedContainers = getReceivedContainers();

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("343242132");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList =
        getInstructionDownloadBlobDataDTOS();

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);

    // Mock methods
    Container consolidatedContainer = new Container();
    consolidatedContainer.setAsnNumber("asnNbr");
    consolidatedContainer.setTrackingId("trackingId");
    consolidatedContainer.setInventoryStatus("invStatus");
    consolidatedContainer.setTrackingId("palletId");
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(consolidatedContainer);
    when(rdcManagedConfig.getEnableSingleTransactionForOffline()).thenReturn(true);

    when(rdcManagedConfig.getDcListEligibleForPrepareConsolidatedContainer())
        .thenReturn(Arrays.asList("6014"));

    List<Container> consolidatedContainerList = new ArrayList<>();
    consolidatedContainerList.add(consolidatedContainer);
    rdcOfflineRecieveUtils.postReceivingUpdatesForOffline(
        instruction,
        deliveryDocumentMap,
        httpHeaders,
        true,
        receivedContainers,
        consolidatedContainerList);
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
  }

  /**
   * Get InstructionsDownloadData For Offline Receiving
   *
   * @return
   * @throws UnsupportedEncodingException
   */
  private List<InstructionDownloadBlobDataDTO> getInstructionDownloadBlobDataDTOS()
      throws UnsupportedEncodingException {
    byte blobData[] = getInstructionsDownloadDataForOfflineRcv();
    String blobJsonData = new String(blobData, StandardCharsets.UTF_8.name());
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList = new ArrayList<>();
    instructionDownloadBlobDataDTOList =
        gson.fromJson(
            blobJsonData, new TypeToken<List<InstructionDownloadBlobDataDTO>>() {}.getType());
    return instructionDownloadBlobDataDTOList;
  }

  /**
   * This test validates postReceivingUpdatesForOffline() when enablePrepareConsolidatedContainers
   * flag is true consolidatedContainerList is prepared but EligibleDcList is not matching
   *
   * @throws ReceivingException
   * @throws IOException
   */
  @Test
  public void testPostReceivingUpdatesForOffline_eligibleDcListNotEmptyButMismatching()
      throws ReceivingException, IOException {
    // Prepare test data
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
    receivedContainer.setLabelTrackingId("1129721548956784");
    receivedContainer.setStoreAlignment("MANUAL");
    receivedContainer.setAsnNumber("1234");
    receivedContainers.add(receivedContainer);

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("1129721548956784");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList =
        getInstructionDownloadBlobDataDTOS();

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);

    // Mock methods
    Container consolidatedContainer = new Container();
    consolidatedContainer.setAsnNumber("asnNbr");
    consolidatedContainer.setTrackingId("1129721548956784");
    consolidatedContainer.setInventoryStatus("invStatus");
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put("labelType", "XDK2");
    containerMiscInfo.put(RdcConstants.ORIGIN_FACILITY_NUM, "6014");
    consolidatedContainer.setContainerMiscInfo(containerMiscInfo);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(consolidatedContainer);
    when(rdcManagedConfig.getEnableSingleTransactionForOffline()).thenReturn(true);

    when(rdcManagedConfig.getDcListEligibleForPrepareConsolidatedContainer())
        .thenReturn(Arrays.asList("32899"));

    rdcOfflineRecieveUtils.postReceivingUpdatesForOffline(
        instruction,
        deliveryDocumentMap,
        httpHeaders,
        true,
        receivedContainers,
        Arrays.asList(consolidatedContainer));
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
  }

  /**
   * This test validates postReceivingUpdatesForOffline() when enablePrepareConsolidatedContainers
   * flag is true consolidatedContainerList is prepared and EligibleDcList is matching
   *
   * @throws ReceivingException
   * @throws IOException
   */
  @Test
  public void testPostReceivingUpdatesForOffline_eligibleDcListNotEmptyAndMatching()
      throws ReceivingException, IOException {
    // Prepare test data
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
    receivedContainer.setLabelTrackingId("1129721548956784");
    receivedContainer.setStoreAlignment("MANUAL");
    receivedContainer.setAsnNumber("1234");
    receivedContainers.add(receivedContainer);

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("1129721548956784");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList =
        getInstructionDownloadBlobDataDTOS();

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);

    // Mock methods
    Container consolidatedContainer = new Container();
    consolidatedContainer.setAsnNumber("asnNbr");
    consolidatedContainer.setTrackingId("1129721548956784");
    consolidatedContainer.setInventoryStatus("invStatus");
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put("labelType", "XDK2");
    containerMiscInfo.put(RdcConstants.ORIGIN_FACILITY_NUM, "6014");
    consolidatedContainer.setContainerMiscInfo(containerMiscInfo);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(consolidatedContainer);
    when(rdcManagedConfig.getEnableSingleTransactionForOffline()).thenReturn(true);

    when(rdcManagedConfig.getDcListEligibleForPrepareConsolidatedContainer())
        .thenReturn(Arrays.asList("6014"));

    rdcOfflineRecieveUtils.postReceivingUpdatesForOffline(
        instruction,
        deliveryDocumentMap,
        httpHeaders,
        true,
        receivedContainers,
        Arrays.asList(consolidatedContainer));
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
  }

  /**
   * This test validates postReceivingUpdatesForOffline() when enablePrepareConsolidatedContainers
   * flag is true consolidatedContainerList is prepared and EligibleDcList is matching and pallet id
   * is present for the container
   *
   * @throws ReceivingException
   * @throws IOException
   */
  @Test
  public void testPostReceivingUpdatesForOffline_eligibleDcListNotEmptyAndMatching_palletIdPresent()
      throws ReceivingException, IOException {
    // Prepare test data
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
    receivedContainer.setLabelTrackingId("1129721548956784");
    receivedContainer.setStoreAlignment("MANUAL");
    receivedContainer.setAsnNumber("1234");
    receivedContainer.setPalletId("palletId");
    receivedContainers.add(receivedContainer);

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("1129721548956784");
    deliveryDocument.setPalletId("palletId");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList =
        getInstructionDownloadBlobDataDTOS();

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);

    // Mock methods
    Container consolidatedContainer = new Container();
    consolidatedContainer.setAsnNumber("asnNbr");
    consolidatedContainer.setTrackingId("palletId");
    consolidatedContainer.setInventoryStatus("invStatus");
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put("labelType", "XDK2");
    containerMiscInfo.put(RdcConstants.ORIGIN_FACILITY_NUM, "6014");
    consolidatedContainer.setContainerMiscInfo(containerMiscInfo);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(consolidatedContainer);
    when(rdcManagedConfig.getEnableSingleTransactionForOffline()).thenReturn(true);

    when(rdcManagedConfig.getDcListEligibleForPrepareConsolidatedContainer())
        .thenReturn(Arrays.asList("6014"));

    rdcOfflineRecieveUtils.postReceivingUpdatesForOffline(
        instruction,
        deliveryDocumentMap,
        httpHeaders,
        true,
        receivedContainers,
        Arrays.asList(consolidatedContainer));
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
  }

  /**
   * This test validates postReceivingUpdatesForOffline() when enablePrepareConsolidatedContainers
   * flag is true consolidatedContainerList is prepared and All dc eligible
   *
   * @throws ReceivingException
   * @throws IOException
   */
  @Test
  public void testPostReceivingUpdatesForOffline_AllDcEligible()
      throws ReceivingException, IOException {
    // Prepare test data
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
    receivedContainer.setLabelTrackingId("1129721548956784");
    receivedContainer.setStoreAlignment("MANUAL");
    receivedContainer.setAsnNumber("1234");
    receivedContainers.add(receivedContainer);

    Instruction instruction = MockRdcInstruction.getInstruction();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("1129721548956784");
    Map<String, DeliveryDocument> deliveryDocumentMap = new HashMap<>();
    deliveryDocumentMap.put(deliveryDocument.getTrackingId(), deliveryDocument);
    deliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    List<InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOList =
        getInstructionDownloadBlobDataDTOS();

    Map<String, InstructionDownloadBlobDataDTO> instructionDownloadBlobDataDTOMap = new HashMap<>();
    instructionDownloadBlobDataDTOMap.put(
        deliveryDocument.getTrackingId(), instructionDownloadBlobDataDTOList.get(0));
    instructionDownloadMessageDTO.setMiscOfflineRcvInfoMap(instructionDownloadBlobDataDTOMap);

    // Mock methods
    Container consolidatedContainer = new Container();
    consolidatedContainer.setAsnNumber("asnNbr");
    consolidatedContainer.setTrackingId("1129721548956784");
    consolidatedContainer.setInventoryStatus("invStatus");
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put("labelType", "XDK2");
    containerMiscInfo.put(RdcConstants.ORIGIN_FACILITY_NUM, "6014");
    consolidatedContainer.setContainerMiscInfo(containerMiscInfo);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(consolidatedContainer);
    when(rdcManagedConfig.getEnableSingleTransactionForOffline()).thenReturn(true);

    when(rdcManagedConfig.getDcListEligibleForPrepareConsolidatedContainer())
        .thenReturn(Collections.EMPTY_LIST);

    rdcOfflineRecieveUtils.postReceivingUpdatesForOffline(
        instruction,
        deliveryDocumentMap,
        httpHeaders,
        true,
        receivedContainers,
        Arrays.asList(consolidatedContainer));
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
  }
}
