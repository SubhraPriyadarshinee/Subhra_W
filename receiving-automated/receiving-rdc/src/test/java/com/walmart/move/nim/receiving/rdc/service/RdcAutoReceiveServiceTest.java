package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeGetLpnsRequest;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersRequestBody;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.TenantSpecificBackendConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.message.publisher.JMSSorterPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaAthenaPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaLabelDataPublisher;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadContainerDTO;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadDistributionsDTO;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadItemDTO;
import com.walmart.move.nim.receiving.core.model.instruction.LabelDataAllocationDTO;
import com.walmart.move.nim.receiving.core.model.label.LabelDataMiscInfo;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import com.walmart.move.nim.receiving.core.model.symbotic.RdcVerificationMessage;
import com.walmart.move.nim.receiving.core.model.symbotic.SymFreightType;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdcInstruction;
import com.walmart.move.nim.receiving.rdc.utils.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.*;
import org.mockito.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcAutoReceiveServiceTest {

  @InjectMocks private RdcAutoReceiveService rdcAutoReceiveService;
  @Mock private RdcDeliveryService rdcDeliveryService;
  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RdcContainerUtils rdcContainerUtils;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private ReceiptService receiptService;
  @Mock private NimRdsService nimRdsService;
  @Mock private RdcInstructionHelper rdcInstructionHelper;
  @Mock private RdcSlottingUtils rdcSlottingUtils;
  @Mock private ContainerService containerService;
  @Mock private RdcReceivingUtils rdcReceivingUtils;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private LabelDataService labelDataService;

  @Mock private PrintJobService printJobService;
  @Mock private VendorBasedDeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler;

  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private RdcInstructionService rdcInstructionService;
  @Mock private TenantSpecificBackendConfig tenantSpecificBackendConfig;
  @Mock private RdcItemValidator rdcItemValidator;
  @Mock private KafkaLabelDataPublisher labelDataPublisher;
  @Mock private InstructionService instructionService;
  @Mock private LabelDownloadEventService labelDownloadEventService;
  @Mock private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Mock private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Mock private RdcDeliveryMetaDataService rdcDeliveryMetaDataService;
  @Mock private LocationService locationService;
  @Mock AppConfig appConfig;
  @Mock private RdcDaService rdcDaService;
  @Mock private RdcAutoReceivingUtils rdcAutoReceivingUtils;
  @Mock private KafkaAthenaPublisher kafkaAthenaPublisher;
  @Mock private JMSSorterPublisher jmsSorterPublisher;

  private HttpHeaders httpHeaders;
  private String facilityNum = "32818";
  private String facilityCountryCode = "us";
  private Gson gson;
  private final List<String> VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS =
      Arrays.asList("CC", "CI", "CJ");

  @BeforeClass
  public void initMocks() {
    gson = new Gson();
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(rdcAutoReceiveService, "gson", gson);
    ReflectionTestUtils.setField(rdcAutoReceivingUtils, "gson", gson);
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
        rdcDeliveryService,
        rdcInstructionUtils,
        tenantSpecificConfigReader,
        rdcContainerUtils,
        containerPersisterService,
        receiptService,
        nimRdsService,
        rdcInstructionHelper,
        rdcSlottingUtils,
        containerService,
        rdcReceivingUtils,
        labelDataService,
        instructionPersisterService,
        printJobService,
        deliveryDocumentsSearchHandler,
        rdcInstructionService,
        rdcItemValidator,
        labelDataPublisher,
        labelDownloadEventService,
        appConfig,
        symboticPutawayPublishHelper);
  }

  @Test
  public void testAutoReceiveContainerLpns_Success_DA() throws ReceivingException, IOException {
    AutoReceiveRequest autoReceiveRequest = getAutoReceiveRequestForDA();
    autoReceiveRequest.setMessageId(null);
    ContainerItem containerItem = new ContainerItem();
    containerItem.setItemNumber(1232323L);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).setPackType("C");
    when(rdcAutoReceivingUtils.createInstruction(
            any(AutoReceiveRequest.class), any(DeliveryDocument.class), any(HttpHeaders.class)))
        .thenReturn(MockRdcInstruction.getInstruction());
    doCallRealMethod()
        .when(rdcAutoReceivingUtils)
        .getReceivedContainerInfo(anyString(), anyString());
    doNothing()
        .when(rdcAutoReceivingUtils)
        .buildContainerItemAndContainerForDA(
            any(AutoReceiveRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            any(ReceivedContainer.class),
            anyLong(),
            anyString());
    when(rdcAutoReceivingUtils.getGdmDeliveryDocuments(
            any(AutoReceiveRequest.class), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    doCallRealMethod()
        .when(rdcAutoReceivingUtils)
        .transformLabelData(anyList(), any(DeliveryDocument.class));
    when(rdcAutoReceivingUtils.fetchLabelData(
            anyList(), any(AutoReceiveRequest.class), anyBoolean()))
        .thenReturn(getLabelDataForDA().get(0));
    when(rdcDeliveryService.getDeliveryDocumentsByPoAndPoLineFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_AUTOMATION_OUTBOX_PATTERN_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(MockRdcInstruction.getContainer());
    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), anyString(), anyString(), anyInt()))
        .thenReturn(Collections.emptyList());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(labelDataService.save(any(LabelData.class))).thenAnswer(i -> i.getArguments()[0]);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_AUTOMATION_OUTBOX_PATTERN_ENABLED,
            false))
        .thenReturn(false);

    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));
    doNothing().when(rdcContainerUtils).postReceiptsToDcFin(any(Container.class), anyString());
    doNothing().when(rdcContainerUtils).publishContainerToEI(any(Container.class), any());

    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());

    InstructionResponse instructionResponse =
        rdcAutoReceiveService.autoReceiveContainerLpns(autoReceiveRequest, httpHeaders);

    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
    verify(rdcContainerUtils, times(1)).postReceiptsToDcFin(any(Container.class), anyString());
    verify(rdcContainerUtils, times(1)).publishContainerToEI(any(Container.class), any());

    verify(rdcReceivingUtils, times(1))
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), eq(null), anyString(), anyInt());
    Assert.assertNotNull(instructionResponse);
    Assert.assertNotNull(instructionResponse.getDeliveryDocuments());
  }

  @Test
  public void testAutoReceiveContainerLpns_ExceptionHandling_Success_DA()
      throws ReceivingException, IOException {
    AutoReceiveRequest autoReceiveRequest = getAutoReceiveRequestForDA();
    autoReceiveRequest.setFeatureType(RdcConstants.EXCEPTION_HANDLING);
    autoReceiveRequest.setMessageId(null);
    List<DeliveryDocument> deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocument
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CI");
    autoReceiveRequest.setDeliveryDocuments(deliveryDocument);
    autoReceiveRequest.setLpn(null);
    ContainerItem containerItem = new ContainerItem();
    containerItem.setItemNumber(1232323L);
    List<DeliveryDocument> deliveryDocumentList = autoReceiveRequest.getDeliveryDocuments();
    when(rdcAutoReceivingUtils.createInstruction(
            any(AutoReceiveRequest.class), any(DeliveryDocument.class), any(HttpHeaders.class)))
        .thenReturn(MockRdcInstruction.getInstruction());
    doCallRealMethod()
        .when(rdcAutoReceivingUtils)
        .getReceivedContainerInfo(anyString(), anyString());
    doNothing()
        .when(rdcAutoReceivingUtils)
        .buildContainerItemAndContainerForDA(
            any(AutoReceiveRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            any(ReceivedContainer.class),
            anyLong(),
            anyString());
    when(rdcAutoReceivingUtils.getGdmDeliveryDocuments(
            any(AutoReceiveRequest.class), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    doCallRealMethod()
        .when(rdcAutoReceivingUtils)
        .transformLabelData(anyList(), any(DeliveryDocument.class));
    when(rdcAutoReceivingUtils.fetchLabelData(
            anyList(), any(AutoReceiveRequest.class), anyBoolean()))
        .thenReturn(getLabelDataForDA().get(0));
    doReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS)
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();
    when(labelDataService.findByTrackingId(anyString())).thenReturn(getLabelDataForDA().get(0));
    when(hawkeyeRestApiClient.getLpnsFromHawkeye(
            any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class)))
        .thenReturn(Optional.of(Arrays.asList("a602042323232323")));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_AUTOMATION_OUTBOX_PATTERN_ENABLED,
            false))
        .thenReturn(false);
    doNothing().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocumentList);
    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), anyString(), anyString(), anyInt()))
        .thenReturn(Collections.emptyList());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(labelDataService.save(any(LabelData.class))).thenAnswer(i -> i.getArguments()[0]);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(MockRdcInstruction.getContainer());

    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));
    doNothing().when(rdcContainerUtils).postReceiptsToDcFin(any(Container.class), anyString());
    doNothing().when(rdcContainerUtils).publishContainerToEI(any(Container.class), any());

    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());

    InstructionResponse instructionResponse =
        rdcAutoReceiveService.autoReceiveContainerLpns(autoReceiveRequest, httpHeaders);

    verify(labelDataService, times(1)).save(any(LabelData.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());

    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
    verify(rdcContainerUtils, times(1)).postReceiptsToDcFin(any(Container.class), anyString());
    verify(rdcContainerUtils, times(1)).publishContainerToEI(any(Container.class), any());

    verify(rdcReceivingUtils, times(1))
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), eq(null), anyString(), anyInt());
    verify(symboticPutawayPublishHelper, times(1))
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));
    Assert.assertNotNull(instructionResponse);
    Assert.assertNotNull(instructionResponse.getDeliveryDocuments());
  }

  @Test
  public void testAutoReceiveContainerLpns_ExceptionHandling_InvalidPackAndHandlingCode_Success_DA()
      throws ReceivingException, IOException {
    AutoReceiveRequest autoReceiveRequest = getAutoReceiveRequestForDA();
    autoReceiveRequest.setFeatureType(RdcConstants.EXCEPTION_HANDLING);
    autoReceiveRequest.setMessageId(null);
    autoReceiveRequest.setFlibEligible(false);
    List<DeliveryDocument> deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocument
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BI");
    autoReceiveRequest.setDeliveryDocuments(deliveryDocument);
    autoReceiveRequest.setLpn(null);
    ContainerItem containerItem = new ContainerItem();
    containerItem.setItemNumber(1232323L);
    List<DeliveryDocument> deliveryDocumentList = autoReceiveRequest.getDeliveryDocuments();
    when(rdcAutoReceivingUtils.createInstruction(
            any(AutoReceiveRequest.class), any(DeliveryDocument.class), any(HttpHeaders.class)))
        .thenReturn(MockRdcInstruction.getInstruction());
    doCallRealMethod()
        .when(rdcAutoReceivingUtils)
        .getReceivedContainerInfo(anyString(), anyString());
    ArgumentCaptor<ReceivedContainer> captor = ArgumentCaptor.forClass(ReceivedContainer.class);
    doNothing()
        .when(rdcAutoReceivingUtils)
        .buildContainerItemAndContainerForDA(
            any(AutoReceiveRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            captor.capture(),
            anyLong(),
            anyString());
    when(rdcAutoReceivingUtils.getGdmDeliveryDocuments(
            any(AutoReceiveRequest.class), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    doCallRealMethod()
        .when(rdcAutoReceivingUtils)
        .transformLabelData(anyList(), any(DeliveryDocument.class));
    when(rdcAutoReceivingUtils.fetchLabelData(
            anyList(), any(AutoReceiveRequest.class), anyBoolean()))
        .thenReturn(getLabelDataForDA().get(0));
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            anyString(), anyLong(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(getLabelDataForDA());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_AUTOMATION_OUTBOX_PATTERN_ENABLED,
            false))
        .thenReturn(false);
    doNothing().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocumentList);
    doNothing()
        .when(rdcInstructionUtils)
        .validatePoLineIsCancelledOrClosedOrRejected(any(DeliveryDocumentLine.class));
    doNothing()
        .when(rdcReceivingUtils)
        .validateOverage(anyList(), anyInt(), any(HttpHeaders.class), anyBoolean());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(
            i -> {
              Instruction instruction = (Instruction) i.getArguments()[0];
              instruction.setId(Long.valueOf("23424234324"));
              return instruction;
            });
    when(containerPersisterService.saveContainer(any(Container.class)))
        .thenReturn(MockRdcInstruction.getContainer());
    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), anyString(), anyString(), anyInt()))
        .thenReturn(Collections.emptyList());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(rdcContainerUtils.getContainerDetails(
            anyString(), anyMap(), any(ContainerType.class), anyString()))
        .thenCallRealMethod();
    when(labelDataService.save(any(LabelData.class))).thenAnswer(i -> i.getArguments()[0]);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(MockRdcInstruction.getContainer());

    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));
    doNothing().when(rdcContainerUtils).postReceiptsToDcFin(any(Container.class), anyString());
    doNothing().when(rdcContainerUtils).publishContainerToEI(any(Container.class), any());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_SORTER_ENABLED_ON_KAFKA), any(Boolean.class)))
        .thenReturn(true);
    doNothing().when(kafkaAthenaPublisher).publishLabelToSorter(any(Container.class), anyString());
    when(rdcReceivingUtils.getLabelTypeForSorterDivert(
            any(ReceivedContainer.class), any(Container.class)))
        .thenReturn(com.walmart.move.nim.receiving.utils.constants.LabelType.STORE.name());
    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());

    InstructionResponse instructionResponse =
        rdcAutoReceiveService.autoReceiveContainerLpns(autoReceiveRequest, httpHeaders);
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
    verify(rdcContainerUtils, times(1)).postReceiptsToDcFin(any(Container.class), anyString());
    verify(rdcContainerUtils, times(1)).publishContainerToEI(any(Container.class), any());
    verify(rdcReceivingUtils, times(1))
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), eq(null), anyString(), anyInt());
    verify(symboticPutawayPublishHelper, times(0))
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));
    Assert.assertNotNull(instructionResponse);
    Assert.assertNotNull(instructionResponse.getDeliveryDocuments());
    Assert.assertEquals(
        captor.getValue().getFulfillmentMethod(),
        FulfillmentMethodType.CASE_PACK_RECEIVING.getType());
  }

  @Test
  public void testAutoReceiveLpn_bypassMessageType() throws ReceivingException {
    RdcAutoReceiveService rdcAutoReceiveServiceTemp = Mockito.spy(rdcAutoReceiveService);
    RdcVerificationMessage rdcVerificationMessage = getRdcVerificationMessage();
    rdcVerificationMessage.setMessageType("BYPASS");
    doCallRealMethod()
        .when(rdcAutoReceivingUtils)
        .buildAutoReceiveRequest((any(RdcVerificationMessage.class)));
    doReturn(new InstructionResponseImplNew())
        .when(rdcAutoReceiveServiceTemp)
        .autoReceiveContainerLpns(any(AutoReceiveRequest.class), any(HttpHeaders.class));
    rdcAutoReceiveServiceTemp.autoReceiveOnVerificationEvent(rdcVerificationMessage, httpHeaders);
    verify(rdcAutoReceivingUtils, times(0))
        .buildAutoReceiveRequest(any(RdcVerificationMessage.class));
    verify(rdcAutoReceiveServiceTemp, times(0))
        .autoReceiveContainerLpns(any(AutoReceiveRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testAutoReceiveLpn_normalMessageType() throws ReceivingException {
    RdcAutoReceiveService rdcAutoReceiveServiceTemp = Mockito.spy(rdcAutoReceiveService);
    RdcVerificationMessage rdcVerificationMessage = getRdcVerificationMessage();
    rdcVerificationMessage.setMessageType("NORMAL");
    doCallRealMethod()
        .when(rdcAutoReceivingUtils)
        .buildAutoReceiveRequest((any(RdcVerificationMessage.class)));
    doReturn(new InstructionResponseImplNew())
        .when(rdcAutoReceiveServiceTemp)
        .autoReceiveContainerLpns(any(AutoReceiveRequest.class), any(HttpHeaders.class));
    try {
      rdcAutoReceiveServiceTemp.autoReceiveOnVerificationEvent(rdcVerificationMessage, httpHeaders);
    } catch (ReceivingBadDataException e) {
      verify(rdcAutoReceivingUtils, times(1))
          .buildAutoReceiveRequest(any(RdcVerificationMessage.class));
    }
  }

  @Test
  public void testAutoReceiveLpn_otherMessageTypes() throws ReceivingException {
    RdcAutoReceiveService rdcAutoReceiveServiceTemp = Mockito.spy(rdcAutoReceiveService);
    RdcVerificationMessage rdcVerificationMessage = getRdcVerificationMessage();
    rdcVerificationMessage.setMessageType("");
    rdcAutoReceiveServiceTemp.autoReceiveOnVerificationEvent(rdcVerificationMessage, httpHeaders);
    verify(rdcAutoReceiveServiceTemp, times(0))
        .autoReceiveContainerLpns(any(AutoReceiveRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testAutoReceiveContainerLpns_SSTK_success() throws ReceivingException, IOException {
    AutoReceiveRequest autoReceiveRequest = getAutoReceiveRequestForDA();
    autoReceiveRequest.setMessageId(null);
    List<DeliveryDocument> deliveryDocument = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    deliveryDocument
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CI");
    autoReceiveRequest.setDeliveryDocuments(deliveryDocument);
    List<DeliveryDocument> deliveryDocumentList = autoReceiveRequest.getDeliveryDocuments();
    when(rdcAutoReceivingUtils.createInstruction(
            any(AutoReceiveRequest.class), any(DeliveryDocument.class), any(HttpHeaders.class)))
        .thenReturn(MockRdcInstruction.getInstruction());
    doCallRealMethod()
        .when(rdcAutoReceivingUtils)
        .buildReceiveInstructionRequest(any(AutoReceiveRequest.class), any(DeliveryDocument.class));
    doReturn(MockRdcInstruction.getAutoSlotFromSlotting())
        .when(rdcSlottingUtils)
        .receiveContainers(
            any(ReceiveInstructionRequest.class),
            anyString(),
            any(HttpHeaders.class),
            nullable(ReceiveContainersRequestBody.class));
    doCallRealMethod()
        .when(rdcAutoReceivingUtils)
        .buildReceivedContainerForSSTK(anyString(), any(DeliveryDocumentLine.class));
    doNothing()
        .when(rdcAutoReceivingUtils)
        .buildContainerAndContainerItemForSSTK(
            any(Instruction.class),
            any(DeliveryDocument.class),
            any(AutoReceiveRequest.class),
            anyString(),
            anyString(),
            anyString());
    when(rdcAutoReceivingUtils.getGdmDeliveryDocuments(
            any(AutoReceiveRequest.class), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    doCallRealMethod()
        .when(rdcAutoReceivingUtils)
        .transformLabelData(anyList(), any(DeliveryDocument.class));
    when(rdcAutoReceivingUtils.fetchLabelData(
            anyList(), any(AutoReceiveRequest.class), anyBoolean()))
        .thenReturn(getLabelDataForDA().get(0));
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            anyString(), anyLong(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(getLabelDataForDA());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_AUTOMATION_OUTBOX_PATTERN_ENABLED,
            false))
        .thenReturn(false);
    doNothing().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocumentList);
    doNothing()
        .when(rdcInstructionUtils)
        .validatePoLineIsCancelledOrClosedOrRejected(any(DeliveryDocumentLine.class));
    doNothing()
        .when(rdcReceivingUtils)
        .validateOverage(anyList(), anyInt(), any(HttpHeaders.class), anyBoolean());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(
            i -> {
              Instruction instruction = (Instruction) i.getArguments()[0];
              instruction.setId(Long.valueOf("23424234324"));
              return instruction;
            });
    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), anyString(), anyString(), anyInt()))
        .thenReturn(Collections.emptyList());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(getMockPrintJob());
    when(rdcContainerUtils.getContainerDetails(
            anyString(), anyMap(), any(ContainerType.class), anyString()))
        .thenCallRealMethod();
    when(labelDataService.save(any(LabelData.class))).thenAnswer(i -> i.getArguments()[0]);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(MockRdcInstruction.getContainer());
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .getLabelFormatForPallet(any(DeliveryDocumentLine.class));

    doNothing().when(rdcContainerUtils).publishContainersToInventory(any(Container.class));
    doNothing().when(rdcContainerUtils).postReceiptsToDcFin(any(Container.class), anyString());
    doNothing().when(rdcContainerUtils).publishContainerToEI(any(Container.class), any());

    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());

    InstructionResponse instructionResponse =
        rdcAutoReceiveService.autoReceiveContainerLpns(autoReceiveRequest, httpHeaders);

    verify(labelDataService, times(1)).save(any(LabelData.class));
    verify(rdcContainerUtils, times(1)).publishContainersToInventory(any(Container.class));
    verify(rdcContainerUtils, times(1)).postReceiptsToDcFin(any(Container.class), anyString());
    verify(rdcContainerUtils, times(0)).publishContainerToEI(any(Container.class), any());
    verify(rdcReceivingUtils, times(1))
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    verify(receiptService, times(1))
        .createReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), anyInt(), eq(null), anyString());
    verify(symboticPutawayPublishHelper, times(0))
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));
    Assert.assertNotNull(instructionResponse);
    Assert.assertNotNull(instructionResponse.getDeliveryDocuments());
  }

  private RdcVerificationMessage getRdcVerificationMessage() {
    RdcVerificationMessage rdcVerificationMessage = new RdcVerificationMessage();
    rdcVerificationMessage.setLpn("a602042323232323");
    rdcVerificationMessage.setMessageType("NORMAL");
    rdcVerificationMessage.setDeliveryNumber("234234234");
    return rdcVerificationMessage;
  }

  private AutoReceiveRequest getAutoReceiveRequestForSSTK() {
    AutoReceiveRequest autoReceiveRequest = new AutoReceiveRequest();
    autoReceiveRequest.setLpn("a602042323232323");
    autoReceiveRequest.setDoorNumber("423");
    autoReceiveRequest.setDeliveryNumber(232323323L);
    autoReceiveRequest.setQuantity(1);
    autoReceiveRequest.setQuantityUOM("ZA");
    autoReceiveRequest.setPurchaseReferenceNumber("5232232323");
    autoReceiveRequest.setPurchaseReferenceLineNumber(1);
    autoReceiveRequest.setMessageId("35345-4432323");
    return autoReceiveRequest;
  }

  private AutoReceiveRequest getAutoReceiveRequest() {
    AutoReceiveRequest autoReceiveRequest = new AutoReceiveRequest();
    autoReceiveRequest.setLpn("a602042323232323");
    autoReceiveRequest.setDoorNumber("423");
    autoReceiveRequest.setDeliveryNumber(232323323L);
    autoReceiveRequest.setQuantity(1);
    autoReceiveRequest.setQuantityUOM("ZA");
    autoReceiveRequest.setPurchaseReferenceNumber("5232232323");
    autoReceiveRequest.setPurchaseReferenceLineNumber(1);
    autoReceiveRequest.setMessageId("35345-4432323");
    return autoReceiveRequest;
  }

  private List<LabelData> getLabelDataForSSTK() {
    LabelData labelData = new LabelData();
    labelData.setPurchaseReferenceNumber("5232232323");
    labelData.setPurchaseReferenceLineNumber(1);
    labelData.setDeliveryNumber(232323323L);
    labelData.setTrackingId("a602042323232323");
    labelData.setStatus("AVAILABLE");
    return Collections.singletonList(labelData);
  }

  private AutoReceiveRequest getAutoReceiveRequestForDA() {
    AutoReceiveRequest autoReceiveRequest = new AutoReceiveRequest();
    autoReceiveRequest.setLpn("a602042323232323");
    autoReceiveRequest.setDoorNumber("423");
    autoReceiveRequest.setDeliveryNumber(232323323L);
    autoReceiveRequest.setQuantity(1);
    autoReceiveRequest.setQuantityUOM("ZA");
    autoReceiveRequest.setPurchaseReferenceNumber("5232232323");
    autoReceiveRequest.setPurchaseReferenceLineNumber(1);
    autoReceiveRequest.setMessageId("35345-4432323");
    autoReceiveRequest.setFlibEligible(true);
    return autoReceiveRequest;
  }

  private List<LabelData> getLabelDataForDA() {
    LabelData labelData = new LabelData();
    labelData.setPurchaseReferenceNumber("5232232323");
    labelData.setPurchaseReferenceLineNumber(1);
    labelData.setDeliveryNumber(232323323L);
    labelData.setTrackingId("a602042323232323");
    LabelDataAllocationDTO allocation = getMockLabelDataAllocationDTO();
    labelData.setAllocation(allocation);
    labelData.setStatus("AVAILABLE");
    LabelDataMiscInfo labelDataMiscInfo = new LabelDataMiscInfo();
    labelDataMiscInfo.setSlotType("Prime");
    labelDataMiscInfo.setAsrsAlignment("SYM2");
    labelDataMiscInfo.setLocationSize(72);
    labelDataMiscInfo.setLocation("M1608");
    labelData.setLabelDataMiscInfo(gson.toJson(labelDataMiscInfo));
    return Collections.singletonList(labelData);
  }

  private LabelDataAllocationDTO getMockLabelDataAllocationDTO() {
    LabelDataAllocationDTO allocation = new LabelDataAllocationDTO();
    InstructionDownloadContainerDTO container = new InstructionDownloadContainerDTO();
    container.setTrackingId("a602042323232323");
    container.setCtrType("CASE");
    container.setOutboundChannelMethod("DA");
    InstructionDownloadDistributionsDTO distributions = new InstructionDownloadDistributionsDTO();
    InstructionDownloadItemDTO item = new InstructionDownloadItemDTO();
    item.setItemNbr(658790758L);
    item.setAisle("12");
    item.setItemUpc("78236478623");
    item.setPickBatch("281");
    item.setPrintBatch("281");
    item.setZone("03");
    item.setVnpk(1);
    item.setWhpk(1);
    distributions.setItem(item);
    distributions.setOrderId("1234");
    distributions.setQtyUom("ZA");
    distributions.setAllocQty(1);
    container.setDistributions(Collections.singletonList(distributions));
    Facility finalDestination = new Facility();
    finalDestination.setBuNumber("87623");
    finalDestination.setCountryCode("US");
    container.setFinalDestination(finalDestination);
    allocation.setContainer(container);
    return allocation;
  }

  private static PrintJob getMockPrintJob() {
    PrintJob printJob = new PrintJob();
    printJob.setInstructionId(45254245L);
    printJob.setId(76567L);
    printJob.setDeliveryNumber(768688L);
    printJob.setCreateUserId("sysadmin");
    printJob.setLabelIdentifier(Collections.singleton("q0602000000000234234234"));
    return printJob;
  }

  @Test
  public void testPersistLabelInfo_ReceivingBadDataException() throws ReceivingBadDataException {
    doThrow(ReceivingBadDataException.class)
        .when(labelDownloadEventService)
        .findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumber(
            anyLong(), anyString(), anyLong());
    verify(labelDownloadEventService, times(0)).saveAll(any());
  }

  private static LabelData getMockLabelData() {
    return LabelData.builder()
        .deliveryNumber(94769060L)
        .purchaseReferenceNumber("3615852071")
        .isDAConveyable(Boolean.TRUE)
        .itemNumber(566051127L)
        .lpnsCount(6)
        .labelSequenceNbr(20231016000100001L)
        .labelType(LabelType.ORDERED)
        .build();
  }
}
