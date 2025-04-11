package com.walmart.move.nim.receiving.wfs.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.app.TenantSpecificReportConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryEachesResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.model.UniversalInstructionResponse;
import com.walmart.move.nim.receiving.core.model.docktag.*;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.GdmLpnDetailsResponse;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import com.walmart.move.nim.receiving.wfs.mock.data.MockDockTag;
import io.strati.libs.commons.lang3.RandomUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.CollectionUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WFSDockTagServiceTest {

  @Mock private PrintJobService printJobService;
  @Mock private TenantSpecificReportConfig tenantSpecificReportConfig;
  @Mock private DockTagPersisterService dockTagPersisterService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private InstructionHelperService instructionHelperService;
  @Mock private AppConfig appConfig;
  @Mock private DeliveryServiceRetryableImpl deliveryService;
  @Mock private InventoryService inventoryService;
  @Mock private LPNCacheService lpnCacheService;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private InstructionRepository instructionRepository;
  @Mock private WFSInstructionService wfsInstructionService;
  @Mock private ReceiptService receiptService;
  @Mock private ContainerService containerService;
  @Mock private DeliveryDocumentHelper deliveryDocumentHelper;
  @Mock private WFSLabelIdProcessor wfsLabelIdProcessor;

  @Spy @InjectMocks private WFSDockTagService wfsDockTagService;

  private HttpHeaders headers;
  private Gson gson =
      new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();

  private Gson gsonForDate =
      new GsonBuilder()
          .registerTypeAdapter(Date.class, new GsonUTCDateAdapter("yyyy-MM-dd"))
          .create();
  @Captor private ArgumentCaptor<List<Long>> deliveryCaptor;
  @Captor private ArgumentCaptor<List<InstructionStatus>> instructionStatusCaptor;
  @Captor private ArgumentCaptor<List<DockTag>> dockTagCaptor;

  private static final String facilityNum = "32818";
  private static final String countryCode = "US";

  private List<ReceiptSummaryResponse> receiptSummaryEachesResponse;

  private String GDMFetchedDeliveryDetailsPath =
      "../receiving-test/src/main/resources/json/GDMDeliveryDocument.json";

  private String GDMLPNDetailsResponsePath =
      "../receiving-test/src/main/resources/json/GdmLpnDetailsResponse.json";

  InstructionResponse instructionResponse;

  @BeforeClass
  public void initMocks() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);
    ReflectionTestUtils.setField(wfsDockTagService, "labelIdProcessor", wfsLabelIdProcessor);

    receiptSummaryEachesResponse = new ArrayList<>();
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140004", 1, null, Long.valueOf(96)));
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140005", 1, null, Long.valueOf(96)));
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140007", 1, null, Long.valueOf(144)));

    Instruction instruction1 = new Instruction();
    instruction1.setId(1l);
    instruction1.setChildContainers(null);
    instruction1.setCreateTs(new Date());
    instruction1.setCreateUserId("sysadmin");
    instruction1.setLastChangeTs(new Date());
    instruction1.setLastChangeUserId("sysadmin");
    instruction1.setCompleteTs(new Date());
    instruction1.setCompleteUserId("sysadmin");
    instruction1.setDeliveryNumber(Long.valueOf("21119003"));
    instruction1.setGtin("00000943037194");
    instruction1.setInstructionCode("Build Container");
    instruction1.setInstructionMsg("Build the Container");
    instruction1.setItemDescription("HEM VALUE PACK (4)");
    instruction1.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction1.setPoDcNumber("32899");
    instruction1.setPrintChildContainerLabels(true);
    instruction1.setPurchaseReferenceNumber("9763140004");
    instruction1.setPurchaseReferenceLineNumber(1);
    instruction1.setProjectedReceiveQty(2);
    instruction1.setProviderId("DA");

    InstructionResponseImplNew instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionResponse.setPrintJob(null);

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setDeptNumber("14");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setPoDCNumber("06938");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("33");
    deliveryDocument.setPoTypeCode(33);
    deliveryDocument.setVendorNumber("482497180");
    deliveryDocument.setPurchaseReferenceNumber("4763030227");

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setQtyUOM("EA");
    deliveryDocumentLine.setGtin("00000943037204");
    deliveryDocumentLine.setEvent("POS REPLEN");
    deliveryDocumentLine.setWarehousePackSell(28.18f);
    deliveryDocumentLine.setVendorPackCost(26.98f);
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setOverageQtyLimit(5);
    deliveryDocumentLine.setItemNbr(550129241l);
    deliveryDocumentLine.setPurchaseRefType("CROSSU");
    deliveryDocumentLine.setPalletHigh(4);
    deliveryDocumentLine.setPalletTie(6);
    deliveryDocumentLine.setWeight(9.35f);
    deliveryDocumentLine.setWeightUom("lb");
    deliveryDocumentLine.setCube(0f);
    deliveryDocumentLine.setCubeUom("");
    deliveryDocumentLine.setColor("NONE");
    deliveryDocumentLine.setSize("1.0EA");
    deliveryDocumentLine.setIsHazmat(Boolean.FALSE);
    deliveryDocumentLine.setDescription("TR 12QT STCKPT SS");
    deliveryDocumentLine.setIsConveyable(Boolean.FALSE);
    deliveryDocumentLine.setOpenQty(10);
    deliveryDocumentLine.setOrderableQuantity(9);
    deliveryDocumentLine.setWarehousePackQuantity(9);

    ItemData additionalInfo = new ItemData();
    additionalInfo.setWeightFormatTypeCode("F");
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);

    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);
    instructionResponse.setInstruction(instruction1);
    instructionResponse.setDeliveryDocuments(deliveryDocuments);
    this.instructionResponse = instructionResponse;
  }

  @BeforeMethod
  public void beforeMethod() {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any()))
        .thenReturn(deliveryService);
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        printJobService,
        tenantSpecificReportConfig,
        dockTagPersisterService,
        instructionHelperService,
        deliveryService,
        receiptService,
        containerService,
        inventoryService,
        lpnCacheService,
        containerPersisterService,
        tenantSpecificConfigReader,
        appConfig,
        instructionRepository,
        wfsInstructionService,
        wfsLabelIdProcessor,
        wfsDockTagService);
  }

  @Test
  public void testCreateDockTags_HappyFlow() throws ReceivingException {
    List<DockTag> dockTags = new ArrayList<>();
    List<String> dockTagIds = getLPNDockTags(10);
    when(lpnCacheService.getLPNSBasedOnTenant(10, headers)).thenReturn(dockTagIds);
    when(appConfig.isWftPublishEnabled()).thenReturn(false);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(dockTagPersisterService.saveAllDockTags(anyList())).thenReturn(dockTags);
    when(instructionRepository.saveAll(anyList())).thenReturn(getInstructions(dockTagIds));
    doNothing().when(containerPersisterService).saveContainers(anyList());
    when(printJobService.savePrintJobs(anyList())).thenReturn(new ArrayList<>());
    doReturn(null)
        .when(wfsInstructionService)
        .publishAndGetInstructionResponse(
            any(Container.class), any(Instruction.class), any(HttpHeaders.class));
    doNothing().when(instructionHelperService).publishInstruction(any(), any());
    CreateDockTagRequest createDockTagRequest =
        CreateDockTagRequest.builder()
            .deliveryNumber(12345678L)
            .doorNumber("100")
            .count(10)
            .build();
    DockTagResponse response = wfsDockTagService.createDockTags(createDockTagRequest, headers);
    assertNotNull(response);
    assertNotNull(response.getDockTags());
    assertEquals(response.getDockTags().size(), 10);
    assertEquals(response.getPrintData().getPrintRequests().size(), 10);
    verify(instructionRepository, times(1)).saveAll(anyList());
    verify(containerPersisterService, times(1)).saveContainers(anyList());
    verify(printJobService, times(1)).savePrintJobs(anyList());
    verify(wfsInstructionService, times(10))
        .publishAndGetInstructionResponse(
            any(Container.class), any(Instruction.class), any(HttpHeaders.class));
  }

  @Test
  public void testCreateDockTags_LabelFormat() throws ReceivingException {
    List<DockTag> dockTags = new ArrayList<>();
    List<String> dockTagIds = getLPNDockTags(10);
    when(lpnCacheService.getLPNSBasedOnTenant(10, headers)).thenReturn(dockTagIds);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(dockTagPersisterService.saveAllDockTags(anyList())).thenReturn(dockTags);
    when(instructionRepository.saveAll(anyList())).thenReturn(getInstructions(dockTagIds));
    doNothing().when(containerPersisterService).saveContainers(anyList());
    when(printJobService.savePrintJobs(anyList())).thenReturn(new ArrayList<>());
    doReturn(null)
        .when(wfsInstructionService)
        .publishAndGetInstructionResponse(
            any(Container.class), any(Instruction.class), any(HttpHeaders.class));
    doNothing().when(instructionHelperService).publishInstruction(any(), any());
    CreateDockTagRequest createDockTagRequest =
        CreateDockTagRequest.builder()
            .deliveryNumber(12345678L)
            .doorNumber("100")
            .count(10)
            .build();
    DockTagResponse response = wfsDockTagService.createDockTags(createDockTagRequest, headers);
    assertNotNull(response);
    assertNotNull(response.getDockTags());
    assertEquals(response.getDockTags().size(), 10);
    assertEquals(response.getPrintData().getPrintRequests().size(), 10);

    List<PrintLabelRequest> printLabelRequests = response.getPrintData().getPrintRequests();
    assertEquals(printLabelRequests.size(), 10);
    assertEquals(printLabelRequests.get(0).getFormatName(), "dock_tag_atlas");
    assertFalse(printLabelRequests.get(0).getData().isEmpty());

    List<LabelData> containerTagId =
        printLabelRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals("LPN"))
            .collect(Collectors.toList());
    assertEquals(containerTagId.get(0).getKey(), "LPN");

    List<LabelData> containerCreationTime =
        printLabelRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals("DATE"))
            .collect(Collectors.toList());
    assertEquals(containerCreationTime.get(0).getKey(), "DATE");

    verify(instructionRepository, times(1)).saveAll(anyList());
    verify(containerPersisterService, times(1)).saveContainers(anyList());
    verify(printJobService, times(1)).savePrintJobs(anyList());
    verify(wfsInstructionService, times(10))
        .publishAndGetInstructionResponse(
            any(Container.class), any(Instruction.class), any(HttpHeaders.class));
  }

  @Test
  public void testCreateDockTags_No_Count_Provided() throws ReceivingException {
    List<DockTag> dockTags = new ArrayList<>();
    List<String> dockTagIds = getLPNDockTags(1);
    when(lpnCacheService.getLPNSBasedOnTenant(1, headers)).thenReturn(dockTagIds);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(dockTagPersisterService.saveAllDockTags(anyList())).thenReturn(dockTags);
    when(instructionRepository.saveAll(anyList())).thenReturn(getInstructions(dockTagIds));
    doNothing().when(containerPersisterService).saveContainers(anyList());
    when(printJobService.savePrintJobs(anyList())).thenReturn(new ArrayList<>());
    doReturn(null)
        .when(wfsInstructionService)
        .publishAndGetInstructionResponse(
            any(Container.class), any(Instruction.class), any(HttpHeaders.class));
    doNothing().when(instructionHelperService).publishInstruction(any(), any());
    CreateDockTagRequest createDockTagRequest =
        CreateDockTagRequest.builder()
            .deliveryNumber(12345678L)
            .doorNumber("100")
            .count(null)
            .build();
    DockTagResponse response = wfsDockTagService.createDockTags(createDockTagRequest, headers);
    assertNotNull(response);
    assertNotNull(response.getDockTags());
    assertEquals(response.getDockTags().size(), 1);
    assertEquals(response.getPrintData().getPrintRequests().size(), 1);

    verify(instructionRepository, times(1)).saveAll(anyList());
    verify(containerPersisterService, times(1)).saveContainers(anyList());
    verify(printJobService, times(1)).savePrintJobs(anyList());
    verify(wfsInstructionService, times(1))
        .publishAndGetInstructionResponse(
            any(Container.class), any(Instruction.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testSearchDockTagInvalidDel() {
    List<String> deliveryNumbers = Arrays.asList("a12345");
    SearchDockTagRequest searchDockTagRequest =
        SearchDockTagRequest.builder().deliveryNumbers(deliveryNumbers).build();
    wfsDockTagService.searchDockTag(searchDockTagRequest, null);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testSearchDockTagEmptyDel() {
    List<String> deliveryNumbers = Arrays.asList("");
    SearchDockTagRequest searchDockTagRequest =
        SearchDockTagRequest.builder().deliveryNumbers(deliveryNumbers).build();
    wfsDockTagService.searchDockTag(searchDockTagRequest, null);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testCompleteDockTagsForGivenDeliveries() {
    CompleteDockTagRequestsList completeDockTagRequestsList =
        mock(CompleteDockTagRequestsList.class);
    HttpHeaders httpHeaders = mock(HttpHeaders.class);
    wfsDockTagService.completeDockTagsForGivenDeliveries(completeDockTagRequestsList, httpHeaders);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testReceiveDockTag() {
    ReceiveDockTagRequest receiveDockTagRequest = mock(ReceiveDockTagRequest.class);
    HttpHeaders httpHeaders = mock(HttpHeaders.class);
    wfsDockTagService.receiveDockTag(receiveDockTagRequest, httpHeaders);
  }

  @Test
  public void testSaveDockTag() {
    doNothing().when(dockTagPersisterService).saveDockTag(any());
    DockTag dockTag = mock(DockTag.class);
    wfsDockTagService.saveDockTag(dockTag);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testCompleteDockTag() {
    String dockTagId = "";
    HttpHeaders httpHeaders = mock(HttpHeaders.class);
    wfsDockTagService.completeDockTag(dockTagId, httpHeaders);
  }

  @Test
  public void test_saveDockTag() {
    DockTag mockDockTag = MockDockTag.getDockTag();
    doNothing().when(dockTagPersisterService).saveDockTag(any(DockTag.class));
    wfsDockTagService.saveDockTag(mockDockTag);
    verify(dockTagPersisterService, times(1)).saveDockTag(any(DockTag.class));
  }

  @Test
  public void test_countOfOpenDockTags() {
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList()))
        .thenReturn(1);
    Integer res = wfsDockTagService.countOfOpenDockTags(1L);
    assertNotNull(res);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void test_CompleteDockTagsForGivenDeliveries() {
    doThrow(new ReceivingInternalException(ExceptionCodes.CONFIGURATION_ERROR, "call failed"))
        .when(wfsDockTagService)
        .completeDockTagsForGivenDeliveries(
            any(CompleteDockTagRequestsList.class), any(HttpHeaders.class));
    wfsDockTagService.completeDockTagsForGivenDeliveries(
        new CompleteDockTagRequestsList(), new HttpHeaders());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void test_ReceiveDockTag() {
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.CONFIGURATION_ERROR, "Tenant not supported"))
        .when(wfsDockTagService)
        .receiveDockTag(any(ReceiveDockTagRequest.class), any(HttpHeaders.class));
    wfsDockTagService.receiveDockTag(new ReceiveDockTagRequest(), new HttpHeaders());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void test_CompleteDockTag() {
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.CONFIGURATION_ERROR, "Tenant not supported"))
        .when(wfsDockTagService)
        .completeDockTag(anyString(), any(HttpHeaders.class));
    wfsDockTagService.completeDockTag("123", new HttpHeaders());
  }

  @Test
  public void testSearchDockTag() {
    List<String> deliveryNumbers = Arrays.asList("1234567");
    SearchDockTagRequest searchDockTagRequest =
        SearchDockTagRequest.builder().deliveryNumbers(deliveryNumbers).build();
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(MockDockTag.getDockTag());
    dockTagList.add(MockDockTag.getCompletedDockTag());
    when(dockTagPersisterService.getDockTagsByDeliveries(anyList())).thenReturn(dockTagList);

    assertEquals(
        wfsDockTagService.searchDockTag(searchDockTagRequest, null), gson.toJson(dockTagList));

    verify(dockTagPersisterService, times(1)).getDockTagsByDeliveries(deliveryCaptor.capture());
    assertEquals(deliveryCaptor.getValue().get(0).longValue(), 1234567L);
  }

  @Test
  public void testSearchDockTagWithCreatedStatus() {
    List<String> deliveryNumbers = Arrays.asList("1234567");
    SearchDockTagRequest searchDockTagRequest =
        SearchDockTagRequest.builder().deliveryNumbers(deliveryNumbers).build();
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(MockDockTag.getDockTag());
    when(dockTagPersisterService.getDockTagsByDeliveriesAndStatuses(anyList(), anyList()))
        .thenReturn(dockTagList);

    assertEquals(
        wfsDockTagService.searchDockTag(searchDockTagRequest, InstructionStatus.CREATED),
        gson.toJson(dockTagList));

    verify(dockTagPersisterService, times(1))
        .getDockTagsByDeliveriesAndStatuses(
            deliveryCaptor.capture(), instructionStatusCaptor.capture());
    assertEquals(deliveryCaptor.getValue().get(0).longValue(), 1234567L);
    assertTrue(instructionStatusCaptor.getValue().equals(ReceivingUtils.getPendingDockTagStatus()));
  }

  @Test
  public void testSearchDockTagWithCompletedStatus() {
    List<String> deliveryNumbers = Arrays.asList("1234567");
    SearchDockTagRequest searchDockTagRequest =
        SearchDockTagRequest.builder().deliveryNumbers(deliveryNumbers).build();
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(MockDockTag.getCompletedDockTag());
    when(dockTagPersisterService.getDockTagsByDeliveriesAndStatuses(anyList(), anyList()))
        .thenReturn(dockTagList);

    assertEquals(
        wfsDockTagService.searchDockTag(searchDockTagRequest, InstructionStatus.COMPLETED),
        gson.toJson(dockTagList));

    verify(dockTagPersisterService, times(1))
        .getDockTagsByDeliveriesAndStatuses(
            deliveryCaptor.capture(), instructionStatusCaptor.capture());
    assertEquals(deliveryCaptor.getValue().get(0).longValue(), 1234567L);
    assertTrue(instructionStatusCaptor.getValue().get(0).equals(InstructionStatus.COMPLETED));
  }

  @Test
  public void testCreateContainer() {
    when(containerPersisterService.saveContainer(any(Container.class))).thenReturn(getContainer());

    CreateDockTagRequest createDockTagRequest =
        CreateDockTagRequest.builder().deliveryNumber(12345678L).doorNumber("100").build();

    Container container = wfsDockTagService.getDockTagContainer(createDockTagRequest, headers);

    assertNotNull(container);
    assertTrue(container.getContainerException() == ContainerException.DOCK_TAG.getText());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testCreateDockTag() {
    wfsDockTagService.createDockTag(new CreateDockTagRequest(), MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testUpdateDockTagById() {
    wfsDockTagService.updateDockTagById("dockTagId", InstructionStatus.UPDATED, "sysadmin");
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testCreateDockTag_Params() {
    wfsDockTagService.createDockTag("dockTagId", 1L, "sysadmin", DockTagType.ATLAS_RECEIVING);
  }

  @Test
  public void testCompleteBulkDockTags_NoneOfDockTagsExistsInDb() {
    DockTag dockTag = getDockTag();
    List<String> docktags = Arrays.asList(dockTag.getDockTagId());
    CompleteDockTagRequest completeDockTagRequest =
        CompleteDockTagRequest.builder()
            .deliveryNumber(1234567L)
            .deliveryStatus("WRK")
            .docktags(docktags)
            .build();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(null);
    CompleteDockTagResponse completeDockTagResponse =
        wfsDockTagService.completeDockTags(completeDockTagRequest, httpHeaders);
    assertNotNull(completeDockTagResponse.getFailed());
    assertTrue(CollectionUtils.isEmpty(completeDockTagResponse.getSuccess()));
  }

  @Test
  public void testCompleteBulkDockTagsNoDockTagInDb() {
    DockTag dockTag = MockDockTag.getDockTag();
    List<String> docktags = Arrays.asList(dockTag.getDockTagId());
    CompleteDockTagRequest completeDockTagRequest =
        CompleteDockTagRequest.builder()
            .deliveryNumber(1234567L)
            .deliveryStatus("WRK")
            .docktags(docktags)
            .build();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(null);
    CompleteDockTagResponse completeDockTagResponse =
        wfsDockTagService.completeDockTags(completeDockTagRequest, httpHeaders);
    Assert.assertNotNull(completeDockTagResponse.getFailed());
    assertTrue(CollectionUtils.isEmpty(completeDockTagResponse.getSuccess()));
  }

  @Test
  public void testCompleteBulkDockTags() throws ReceivingException {
    DockTag dockTag = MockDockTag.getDockTag();
    List<String> docktags = Arrays.asList(dockTag.getDockTagId());
    CompleteDockTagRequest completeDockTagRequest =
        CompleteDockTagRequest.builder()
            .deliveryNumber(1234567L)
            .deliveryStatus("WRK")
            .docktags(docktags)
            .build();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(dockTag);
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(dockTagList);
    CompleteDockTagResponse completeDockTagResponse =
        wfsDockTagService.completeDockTags(completeDockTagRequest, httpHeaders);
    assertTrue(CollectionUtils.isEmpty(completeDockTagResponse.getFailed()));
    Assert.assertNotNull(completeDockTagResponse.getSuccess());

    verify(inventoryService, times(1)).deleteContainer(dockTag.getDockTagId(), httpHeaders);

    verify(dockTagPersisterService, times(1)).saveAllDockTags(dockTagCaptor.capture());
    List<DockTag> dockTags = dockTagCaptor.getValue();
    assertEquals(dockTags.size(), 1);
    assertEquals(dockTags.get(0).getDockTagStatus(), InstructionStatus.COMPLETED);
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test
  public void testCompleteBulkDockTagsDeliveryStatusOPN() throws ReceivingException {
    DockTag dockTag = MockDockTag.getDockTag();
    List<String> docktags = Arrays.asList(dockTag.getDockTagId());
    CompleteDockTagRequest completeDockTagRequest =
        CompleteDockTagRequest.builder()
            .deliveryNumber(1234567L)
            .deliveryStatus("OPN")
            .docktags(docktags)
            .build();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(dockTag);
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(dockTagList);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(0);
    doReturn(receiptSummaryEachesResponse)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());

    CompleteDockTagResponse completeDockTagResponse =
        wfsDockTagService.completeDockTags(completeDockTagRequest, httpHeaders);
    assertTrue(CollectionUtils.isEmpty(completeDockTagResponse.getFailed()));
    Assert.assertNotNull(completeDockTagResponse.getSuccess());

    verify(inventoryService, times(1)).deleteContainer(dockTag.getDockTagId(), httpHeaders);

    verify(dockTagPersisterService, times(1)).saveAllDockTags(dockTagCaptor.capture());
    List<DockTag> dockTags = dockTagCaptor.getValue();
    assertEquals(dockTags.size(), 1);
    assertEquals(dockTags.get(0).getDockTagStatus(), InstructionStatus.COMPLETED);
    verify(deliveryService, times(1)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test
  public void testCompleteBulkDockTagsDeliveryStatusOPNButNotLastDT() throws ReceivingException {
    DockTag dockTag = MockDockTag.getDockTag();
    List<String> docktags = Arrays.asList(dockTag.getDockTagId());
    CompleteDockTagRequest completeDockTagRequest =
        CompleteDockTagRequest.builder()
            .deliveryNumber(1234567L)
            .deliveryStatus("OPN")
            .docktags(docktags)
            .build();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(dockTag);
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(dockTagList);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(1);
    doReturn(receiptSummaryEachesResponse)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());

    CompleteDockTagResponse completeDockTagResponse =
        wfsDockTagService.completeDockTags(completeDockTagRequest, httpHeaders);
    assertTrue(CollectionUtils.isEmpty(completeDockTagResponse.getFailed()));
    Assert.assertNotNull(completeDockTagResponse.getSuccess());

    verify(inventoryService, times(1)).deleteContainer(dockTag.getDockTagId(), httpHeaders);

    verify(dockTagPersisterService, times(1)).saveAllDockTags(dockTagCaptor.capture());
    List<DockTag> dockTags = dockTagCaptor.getValue();
    assertEquals(dockTags.size(), 1);
    assertEquals(dockTags.get(0).getDockTagStatus(), InstructionStatus.COMPLETED);
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
    verify(receiptService, times(0)).getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
  }

  @Test
  public void testCompleteBulkDockTagsOneComplete() throws ReceivingException {
    DockTag dockTag = MockDockTag.getDockTag();
    DockTag completeDockTag = MockDockTag.getCompletedDockTag();
    completeDockTag.setDockTagId("c32987000000000000000002");
    List<String> docktags = Arrays.asList(dockTag.getDockTagId(), completeDockTag.getDockTagId());
    CompleteDockTagRequest completeDockTagRequest =
        CompleteDockTagRequest.builder()
            .deliveryNumber(1234567L)
            .deliveryStatus("WRK")
            .docktags(docktags)
            .build();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(dockTag);
    dockTagList.add(completeDockTag);
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(dockTagList);
    CompleteDockTagResponse completeDockTagResponse =
        wfsDockTagService.completeDockTags(completeDockTagRequest, httpHeaders);

    assertTrue(CollectionUtils.isEmpty(completeDockTagResponse.getFailed()));
    assertEquals(completeDockTagResponse.getSuccess().size(), 2);

    verify(inventoryService, times(1)).deleteContainer(dockTag.getDockTagId(), httpHeaders);

    verify(dockTagPersisterService, times(1)).saveAllDockTags(dockTagCaptor.capture());
    List<DockTag> dockTags = dockTagCaptor.getValue();
    assertEquals(dockTags.size(), 2);

    assertEquals(dockTags.get(0).getDockTagStatus(), InstructionStatus.COMPLETED);
    assertEquals(dockTags.get(1).getDockTagStatus(), InstructionStatus.COMPLETED);
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test
  public void testCompleteBulkDockTagsOneNotPresentInDB() throws ReceivingException {
    DockTag dockTag = MockDockTag.getDockTag();
    DockTag completeDockTag = MockDockTag.getCompletedDockTag();
    completeDockTag.setDockTagId("c32987000000000000000002");
    List<String> docktags = Arrays.asList(dockTag.getDockTagId(), completeDockTag.getDockTagId());
    CompleteDockTagRequest completeDockTagRequest =
        CompleteDockTagRequest.builder()
            .deliveryNumber(1234567L)
            .deliveryStatus("WRK")
            .docktags(docktags)
            .build();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(dockTag);
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(dockTagList);
    CompleteDockTagResponse completeDockTagResponse =
        wfsDockTagService.completeDockTags(completeDockTagRequest, httpHeaders);

    assertEquals(completeDockTagResponse.getFailed().get(0), completeDockTag.getDockTagId());
    assertEquals(completeDockTagResponse.getSuccess().get(0), dockTag.getDockTagId());

    verify(inventoryService, times(1)).deleteContainer(dockTag.getDockTagId(), httpHeaders);

    verify(dockTagPersisterService, times(1)).saveAllDockTags(dockTagCaptor.capture());
    List<DockTag> dockTags = dockTagCaptor.getValue();
    assertEquals(dockTags.size(), 1);

    assertEquals(dockTags.get(0).getDockTagStatus(), InstructionStatus.COMPLETED);
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test
  public void testCompleteBulkDockTagsOneInventoryException() throws ReceivingException {
    DockTag dockTag = MockDockTag.getDockTag();
    DockTag dockTag1 = MockDockTag.getDockTag();
    dockTag1.setDockTagId("c32987000000000000000002");
    List<String> docktags = Arrays.asList(dockTag.getDockTagId(), dockTag1.getDockTagId());
    CompleteDockTagRequest completeDockTagRequest =
        CompleteDockTagRequest.builder()
            .deliveryNumber(1234567L)
            .deliveryStatus("WRK")
            .docktags(docktags)
            .build();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(dockTag);
    dockTagList.add(dockTag1);
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(dockTagList);
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.UNABLE_TO_PROCESS_INVENTORY,
                ReceivingConstants.INVENTORY_SERVICE_DOWN))
        .when(inventoryService)
        .deleteContainer(dockTag1.getDockTagId(), httpHeaders);

    CompleteDockTagResponse completeDockTagResponse =
        wfsDockTagService.completeDockTags(completeDockTagRequest, httpHeaders);

    assertEquals(completeDockTagResponse.getFailed().get(0), dockTag1.getDockTagId());
    assertEquals(completeDockTagResponse.getSuccess().get(0), dockTag.getDockTagId());

    verify(inventoryService, times(1)).deleteContainer(dockTag.getDockTagId(), httpHeaders);
    verify(inventoryService, times(1)).deleteContainer(dockTag1.getDockTagId(), httpHeaders);

    verify(dockTagPersisterService, times(1)).saveAllDockTags(dockTagCaptor.capture());
    List<DockTag> dockTags = dockTagCaptor.getValue();
    assertEquals(dockTags.size(), 2);

    List<InstructionStatus> statusList = new ArrayList<>();
    for (DockTag tag : dockTags) {
      statusList.add(tag.getDockTagStatus());
    }
    assertTrue(
        statusList.containsAll(
            Arrays.asList(InstructionStatus.CREATED, InstructionStatus.COMPLETED)));
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test
  public void testReceiveNonConDockTag_HappyFlow_WithAutoReopenEnabled() throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    DockTag nonConDockTag = MockDockTag.getDockTag();
    nonConDockTag.setDeliveryNumber(Long.parseLong("891100"));
    nonConDockTag.setDockTagType(DockTagType.NON_CON);

    String GDMResponseJSON = getJSONStringResponse(GDMFetchedDeliveryDetailsPath);
    when(dockTagPersisterService.getDockTagByDockTagId(nonConDockTag.getDockTagId()))
        .thenReturn(nonConDockTag);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_AUTO_DELIVERY_OPEN)))
        .thenReturn(Boolean.TRUE);
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class)))
        .thenReturn(GDMResponseJSON);
    doNothing()
        .when(instructionHelperService)
        .reopenDeliveryIfNeeded(anyLong(), anyString(), any(HttpHeaders.class), anyString());

    ReceiveNonConDockTagResponse receiveNonConDockTagResponse =
        wfsDockTagService.receiveNonConDockTag(nonConDockTag.getDockTagId(), httpHeaders);

    assertNull(receiveNonConDockTagResponse.getLocationInfo());
    assertEquals(
        receiveNonConDockTagResponse.getDelivery().getDeliveryNumber(),
        (long) nonConDockTag.getDeliveryNumber());
    assertEquals(
        receiveNonConDockTagResponse.getDelivery(),
        gsonForDate.fromJson(GDMResponseJSON, DeliveryDetails.class));

    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(anyString());
    verify(containerService, times(1))
        .publishContainerWithStatus(anyString(), any(HttpHeaders.class), anyString());
    verify(instructionHelperService, times(1))
        .reopenDeliveryIfNeeded(anyLong(), anyString(), any(HttpHeaders.class), anyString());
    verify(dockTagPersisterService, times(1)).saveDockTag(dockTagArgumentCaptor.capture());

    DockTag toBeSavedDockTag = dockTagArgumentCaptor.getValue();
    assertEquals(
        toBeSavedDockTag.getLastChangedUserId(),
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    assertEquals(toBeSavedDockTag.getDockTagStatus(), InstructionStatus.UPDATED);
  }

  @Test
  public void testReceiveNonConDockTag_GDMGetDeliveryException_WithAutoReopenEnabled()
      throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    DockTag nonConDockTag = MockDockTag.getDockTag();
    nonConDockTag.setDeliveryNumber(Long.parseLong("891100"));
    nonConDockTag.setDockTagType(DockTagType.NON_CON);

    when(dockTagPersisterService.getDockTagByDockTagId(nonConDockTag.getDockTagId()))
        .thenReturn(nonConDockTag);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_AUTO_DELIVERY_OPEN)))
        .thenReturn(Boolean.TRUE);
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class)))
        .thenThrow(new ReceivingException("errorMessage"));
    doNothing()
        .when(instructionHelperService)
        .reopenDeliveryIfNeeded(anyLong(), anyString(), any(HttpHeaders.class), anyString());

    ReceiveNonConDockTagResponse receiveNonConDockTagResponse =
        wfsDockTagService.receiveNonConDockTag(nonConDockTag.getDockTagId(), httpHeaders);

    assertNull(receiveNonConDockTagResponse.getLocationInfo());
    // in this case, only the deliveryNumber field is populated (as fetch from GDM errors out), so
    // asserting a bunch of fields
    assertEquals(
        receiveNonConDockTagResponse.getDelivery().getDeliveryNumber(),
        (long) nonConDockTag.getDeliveryNumber());
    assertDeliveryDetailsFieldsNull(receiveNonConDockTagResponse.getDelivery());

    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(anyString());
    verify(containerService, times(1))
        .publishContainerWithStatus(anyString(), any(HttpHeaders.class), anyString());
    verify(instructionHelperService, times(0))
        .reopenDeliveryIfNeeded(anyLong(), anyString(), any(HttpHeaders.class), anyString());
    verify(dockTagPersisterService, times(1)).saveDockTag(dockTagArgumentCaptor.capture());

    DockTag toBeSavedDockTag = dockTagArgumentCaptor.getValue();
    assertEquals(
        toBeSavedDockTag.getLastChangedUserId(),
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    assertEquals(toBeSavedDockTag.getDockTagStatus(), InstructionStatus.UPDATED);
  }

  @Test
  public void testReceiveNonConDockTag_ReopenDeliveryException_WithAutoReopenEnabled()
      throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    DockTag nonConDockTag = MockDockTag.getDockTag();
    nonConDockTag.setDeliveryNumber(Long.parseLong("891100"));
    nonConDockTag.setDockTagType(DockTagType.NON_CON);
    String GDMResponseJSON = getJSONStringResponse(GDMFetchedDeliveryDetailsPath);

    when(dockTagPersisterService.getDockTagByDockTagId(nonConDockTag.getDockTagId()))
        .thenReturn(nonConDockTag);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_AUTO_DELIVERY_OPEN)))
        .thenReturn(Boolean.TRUE);
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class)))
        .thenReturn(GDMResponseJSON);
    doThrow(new ReceivingException("errorMessage"))
        .when(instructionHelperService)
        .reopenDeliveryIfNeeded(anyLong(), anyString(), any(HttpHeaders.class), anyString());

    ReceiveNonConDockTagResponse receiveNonConDockTagResponse =
        wfsDockTagService.receiveNonConDockTag(nonConDockTag.getDockTagId(), httpHeaders);

    assertNull(receiveNonConDockTagResponse.getLocationInfo());
    // in this case, only the fetched GDM DeliveryDetails is returned and then reopenDelivery errors
    // out
    assertEquals(
        receiveNonConDockTagResponse.getDelivery().getDeliveryNumber(),
        (long) nonConDockTag.getDeliveryNumber());
    assertEquals(
        receiveNonConDockTagResponse.getDelivery(),
        gsonForDate.fromJson(GDMResponseJSON, DeliveryDetails.class));

    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(anyString());
    verify(containerService, times(1))
        .publishContainerWithStatus(anyString(), any(HttpHeaders.class), anyString());
    verify(instructionHelperService, times(1))
        .reopenDeliveryIfNeeded(anyLong(), anyString(), any(HttpHeaders.class), anyString());
    verify(dockTagPersisterService, times(1)).saveDockTag(dockTagArgumentCaptor.capture());
    DockTag toBeSavedDockTag = dockTagArgumentCaptor.getValue();
    assertEquals(
        toBeSavedDockTag.getLastChangedUserId(),
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    assertEquals(toBeSavedDockTag.getDockTagStatus(), InstructionStatus.UPDATED);
  }

  @Test
  public void testReceiveNonConDockTag_HappyFlow_WithAutoReopenDisabled()
      throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    DockTag nonConDockTag = MockDockTag.getDockTag();
    nonConDockTag.setDeliveryNumber(Long.parseLong("891100"));
    nonConDockTag.setDockTagType(DockTagType.NON_CON);
    when(dockTagPersisterService.getDockTagByDockTagId(nonConDockTag.getDockTagId()))
        .thenReturn(nonConDockTag);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_AUTO_DELIVERY_OPEN)))
        .thenReturn(Boolean.FALSE);

    ReceiveNonConDockTagResponse receiveNonConDockTagResponse =
        wfsDockTagService.receiveNonConDockTag(nonConDockTag.getDockTagId(), httpHeaders);

    assertNull(receiveNonConDockTagResponse.getLocationInfo());
    // in this case, as feature flag is disabled, the deliveryDetails is created with only
    // deliveryNumber populated
    assertEquals(
        receiveNonConDockTagResponse.getDelivery().getDeliveryNumber(),
        (long) nonConDockTag.getDeliveryNumber());
    assertDeliveryDetailsFieldsNull(receiveNonConDockTagResponse.getDelivery());

    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(anyString());
    verify(containerService, times(1))
        .publishContainerWithStatus(anyString(), any(HttpHeaders.class), anyString());
    verify(instructionHelperService, times(0))
        .reopenDeliveryIfNeeded(anyLong(), anyString(), any(HttpHeaders.class), anyString());
    verify(dockTagPersisterService, times(1)).saveDockTag(dockTagArgumentCaptor.capture());
    DockTag toBeSavedDockTag = dockTagArgumentCaptor.getValue();
    assertEquals(
        toBeSavedDockTag.getLastChangedUserId(),
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    assertEquals(toBeSavedDockTag.getDockTagStatus(), InstructionStatus.UPDATED);
  }

  @Test(expectedExceptions = {ReceivingDataNotFoundException.class})
  public void testReceiveNonConDockTag_Exception_NotFound() throws ReceivingException {
    String inputDockTagId = MockDockTag.getDockTag().getDockTagId();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(dockTagPersisterService.getDockTagByDockTagId(inputDockTagId)).thenReturn(null);
    ReceiveNonConDockTagResponse receiveNonConDockTagResponse =
        wfsDockTagService.receiveNonConDockTag(inputDockTagId, httpHeaders);
    assertNull(receiveNonConDockTagResponse);
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(anyString());
    verify(containerService, times(0))
        .publishContainerWithStatus(anyString(), any(HttpHeaders.class), anyString());
    verify(dockTagPersisterService, times(0)).saveDockTag(any(DockTag.class));
    verify(instructionHelperService, times(0))
        .reopenDeliveryIfNeeded(anyLong(), anyString(), any(HttpHeaders.class), anyString());
  }

  @Test(expectedExceptions = {ReceivingBadDataException.class})
  public void testReceiveNonConDockTag_Exception_Completed() throws ReceivingException {
    DockTag mockDockTag = MockDockTag.getDockTag();
    String inputDockTagId = mockDockTag.getDockTagId();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    mockDockTag.setCompleteTs(new Date());

    when(dockTagPersisterService.getDockTagByDockTagId(inputDockTagId)).thenReturn(mockDockTag);
    ReceiveNonConDockTagResponse receiveNonConDockTagResponse =
        wfsDockTagService.receiveNonConDockTag(inputDockTagId, httpHeaders);
    assertNull(receiveNonConDockTagResponse);
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(anyString());
    verify(containerService, times(0))
        .publishContainerWithStatus(anyString(), any(HttpHeaders.class), anyString());
    verify(dockTagPersisterService, times(0)).saveDockTag(any(DockTag.class));
    verify(instructionHelperService, times(0))
        .reopenDeliveryIfNeeded(anyLong(), anyString(), any(HttpHeaders.class), anyString());
  }

  @Test
  public void testReceiveUniversalTag_HappyFlow() throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    DockTag nonConDockTag = MockDockTag.getDockTag();
    nonConDockTag.setDeliveryNumber(Long.parseLong("891100"));
    nonConDockTag.setDockTagType(DockTagType.NON_CON);
    when(dockTagPersisterService.getDockTagByDockTagId(nonConDockTag.getDockTagId()))
        .thenReturn(nonConDockTag);

    /*when(tenantSpecificConfigReader.getInstructionServiceByFacility(eq(anyString()))).thenReturn(wfsInstructionService);
    ReceiveNonConDockTagResponse receiveNonConDockTagResponse =
            wfsDockTagService.receiveNonConDockTag(nonConDockTag.getDockTagId(), httpHeaders);
    doReturn(null)
            .when(wfsInstructionService)
            .publishWorkingIfNeeded(
                    any(InstructionResponse.class), any(HttpHeaders.class));*/

    String GDMResponseJSON = getJSONStringResponse(GDMFetchedDeliveryDetailsPath);
    when(dockTagPersisterService.getDockTagByDockTagId(nonConDockTag.getDockTagId()))
        .thenReturn(nonConDockTag);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_AUTO_DELIVERY_OPEN)))
        .thenReturn(Boolean.FALSE);
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class)))
        .thenReturn(GDMResponseJSON);
    doNothing()
        .when(instructionHelperService)
        .reopenDeliveryIfNeeded(anyLong(), anyString(), any(HttpHeaders.class), anyString());

    UniversalInstructionResponse receiveNonConDockTagResponse =
        wfsDockTagService.receiveUniversalTag(nonConDockTag.getDockTagId(), "TEST", httpHeaders);
    // in this case, as feature flag is disabled, the deliveryDetails is created with only
    // deliveryNumber populated
    assertEquals(
        receiveNonConDockTagResponse.getDelivery().getDeliveryNumber(),
        (long) nonConDockTag.getDeliveryNumber());
    assertDeliveryDetailsFieldsNull(receiveNonConDockTagResponse.getDelivery());

    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(anyString());
    verify(containerService, times(1))
        .publishContainerWithStatus(anyString(), any(HttpHeaders.class), anyString());
    verify(instructionHelperService, times(0))
        .reopenDeliveryIfNeeded(anyLong(), anyString(), any(HttpHeaders.class), anyString());
    verify(dockTagPersisterService, times(1)).saveDockTag(dockTagArgumentCaptor.capture());
    DockTag toBeSavedDockTag = dockTagArgumentCaptor.getValue();
    assertEquals(
        toBeSavedDockTag.getLastChangedUserId(),
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    assertEquals(toBeSavedDockTag.getDockTagStatus(), InstructionStatus.UPDATED);
  }

  @Test
  public void testReceiveUniversalTag_HappyFlow_WithAutoReopenEnabled() throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    DockTag nonConDockTag = MockDockTag.getDockTag();
    nonConDockTag.setDeliveryNumber(Long.parseLong("891100"));
    nonConDockTag.setDockTagType(DockTagType.NON_CON);

    when(dockTagPersisterService.getDockTagByDockTagId(nonConDockTag.getDockTagId()))
        .thenReturn(nonConDockTag);

    String GDMResponseJSON = getJSONStringResponse(GDMFetchedDeliveryDetailsPath);
    when(dockTagPersisterService.getDockTagByDockTagId(nonConDockTag.getDockTagId()))
        .thenReturn(nonConDockTag);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_AUTO_DELIVERY_OPEN)))
        .thenReturn(Boolean.TRUE);
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class)))
        .thenReturn(GDMResponseJSON);
    doNothing()
        .when(instructionHelperService)
        .reopenDeliveryIfNeeded(anyLong(), anyString(), any(HttpHeaders.class), anyString());

    UniversalInstructionResponse receiveNonConDockTagResponse =
        wfsDockTagService.receiveUniversalTag(nonConDockTag.getDockTagId(), "TEST", httpHeaders);
    // in this case, as feature flag is disabled, the deliveryDetails is created with only
    // deliveryNumber populated
    assertNull(receiveNonConDockTagResponse.getLocationInfo());
    assertEquals(
        receiveNonConDockTagResponse.getDelivery().getDeliveryNumber(),
        (long) nonConDockTag.getDeliveryNumber());
    assertEquals(
        receiveNonConDockTagResponse.getDelivery(),
        gsonForDate.fromJson(GDMResponseJSON, DeliveryDetails.class));

    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(anyString());
    verify(containerService, times(1))
        .publishContainerWithStatus(anyString(), any(HttpHeaders.class), anyString());
    verify(instructionHelperService, times(1))
        .reopenDeliveryIfNeeded(anyLong(), anyString(), any(HttpHeaders.class), anyString());
    verify(dockTagPersisterService, times(1)).saveDockTag(dockTagArgumentCaptor.capture());

    DockTag toBeSavedDockTag = dockTagArgumentCaptor.getValue();
    assertEquals(
        toBeSavedDockTag.getLastChangedUserId(),
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    assertEquals(toBeSavedDockTag.getDockTagStatus(), InstructionStatus.UPDATED);
  }

  @Test
  public void testReceiveUniversalTag_LPN_HappyFlow() throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    DockTag nonConDockTag = MockDockTag.getDockTag();
    nonConDockTag.setDeliveryNumber(Long.parseLong("891100"));

    String GDMResponseJSON = getJSONStringResponse(GDMLPNDetailsResponsePath);
    when(dockTagPersisterService.getDockTagByDockTagId(nonConDockTag.getDockTagId()))
        .thenReturn(null);

    when(tenantSpecificConfigReader.getConfiguredInstance(
            eq(anyString()), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), DeliveryService.class))
        .thenReturn(deliveryService);
    when(deliveryService.getLpnDetailsByLpnNumber(anyString(), any(HttpHeaders.class)))
        .thenReturn(GDMResponseJSON);

    when(tenantSpecificConfigReader.getInstructionServiceByFacility(anyString()))
        .thenReturn(wfsInstructionService);

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId(UUID.nameUUIDFromBytes(new byte[16]).toString());
    instructionRequest.setEnteredQtyUOM("EA");
    instructionRequest.setDeliveryNumber("89305863");
    instructionRequest.setUpcNumber("06062921299332");
    instructionRequest.setMultiSKUItem(false);
    instructionRequest.setReceivingType("UPC");
    instructionRequest.setDoorNumber("TEST");
    when(wfsInstructionService.serveInstructionRequest(anyString(), any(HttpHeaders.class)))
        .thenReturn(instructionResponse);

    UniversalInstructionResponse receiveNonConDockTagResponse =
        wfsDockTagService.receiveUniversalTag(nonConDockTag.getDockTagId(), "TEST", httpHeaders);
    verify(deliveryService, times(1)).getLpnDetailsByLpnNumber(anyString(), any(HttpHeaders.class));

    verify(wfsInstructionService, times(2))
        .serveInstructionRequest(anyString(), any(HttpHeaders.class));
    assertNotNull(receiveNonConDockTagResponse.getInstruction());
    assertNotNull(receiveNonConDockTagResponse.getDeliveryDocuments());
  }

  @Test
  public void testReceiveUniversalTag_Exception_WithAutoReopenEnabled() throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    DockTag nonConDockTag = MockDockTag.getDockTag();
    nonConDockTag.setDeliveryNumber(Long.parseLong("891100"));
    nonConDockTag.setDockTagType(DockTagType.NON_CON);

    when(dockTagPersisterService.getDockTagByDockTagId(nonConDockTag.getDockTagId()))
        .thenReturn(nonConDockTag);

    String GDMResponseJSON = getJSONStringResponse(GDMFetchedDeliveryDetailsPath);
    when(dockTagPersisterService.getDockTagByDockTagId(nonConDockTag.getDockTagId()))
        .thenReturn(nonConDockTag);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_AUTO_DELIVERY_OPEN)))
        .thenReturn(Boolean.TRUE);
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class)))
        .thenReturn(GDMResponseJSON);
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class)))
        .thenThrow(new ReceivingException("errorMessage"));
    doNothing()
        .when(instructionHelperService)
        .reopenDeliveryIfNeeded(anyLong(), anyString(), any(HttpHeaders.class), anyString());

    UniversalInstructionResponse receiveNonConDockTagResponse =
        wfsDockTagService.receiveUniversalTag(nonConDockTag.getDockTagId(), "TEST", httpHeaders);
    // in this case, as feature flag is disabled, the deliveryDetails is created with only
    // deliveryNumber populated
    assertNull(receiveNonConDockTagResponse.getLocationInfo());
    // in this case, only the deliveryNumber field is populated (as fetch from GDM errors out), so
    // asserting a bunch of fields
    assertEquals(
        receiveNonConDockTagResponse.getDelivery().getDeliveryNumber(),
        (long) nonConDockTag.getDeliveryNumber());
    assertDeliveryDetailsFieldsNull(receiveNonConDockTagResponse.getDelivery());

    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(anyString());
    verify(containerService, times(1))
        .publishContainerWithStatus(anyString(), any(HttpHeaders.class), anyString());
    verify(instructionHelperService, times(0))
        .reopenDeliveryIfNeeded(anyLong(), anyString(), any(HttpHeaders.class), anyString());
    verify(dockTagPersisterService, times(1)).saveDockTag(dockTagArgumentCaptor.capture());

    DockTag toBeSavedDockTag = dockTagArgumentCaptor.getValue();
    assertEquals(
        toBeSavedDockTag.getLastChangedUserId(),
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    assertEquals(toBeSavedDockTag.getDockTagStatus(), InstructionStatus.UPDATED);
  }

  // check for proper error code and message!
  @Test
  public void testValidateGdmResponse() throws Exception {
    String GDMResponseJSON = getJSONStringResponse(GDMLPNDetailsResponsePath);
    GdmLpnDetailsResponse gdmLpnDetailsResponse =
        gson.fromJson(GDMResponseJSON, GdmLpnDetailsResponse.class);
    gdmLpnDetailsResponse
        .getPacks()
        .get(0)
        .setReceivingStatus(ReceivingConstants.RECEIVING_STATUS_RECEIVED);
    try {
      wfsDockTagService.validateGDMResponse(gdmLpnDetailsResponse);
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.RE_RECEIVING_LPN_ALREADY_RECEIVED);
      assertEquals(
          e.getMessage(),
          "Error: The re-receiving container 008500550230497831 is already received. Please scan another container!");
    }
  }

  /**
   * A helper method to assert all values of DeliveryDetails object is null except for
   * deliveryNumber
   *
   * @param deliveryDetails
   */
  private void assertDeliveryDetailsFieldsNull(DeliveryDetails deliveryDetails) {
    assertFalse(deliveryDetails.isHazmatInd());
    assertNull(deliveryDetails.getDeliveryStatus());
    assertNull(deliveryDetails.getTrailerId());
    assertNull(deliveryDetails.getDoorNumber());
    assertNull(deliveryDetails.getCarrierCode());
    assertNull(deliveryDetails.getScacCode());
    assertNull(deliveryDetails.getDcNumber());
    assertNull(deliveryDetails.getCountryCode());
    assertNull(deliveryDetails.getDeliveryDocuments());
    assertNull(deliveryDetails.getStateReasonCodes());
    assertNull(deliveryDetails.getDeliveryLegacyStatus());
  }

  private DockTag getDockTag() {
    DockTag dockTag = new DockTag();
    dockTag.setCreateUserId("sysadmin");
    dockTag.setCreateTs(new Date());
    dockTag.setDeliveryNumber(12340001L);
    dockTag.setDockTagId("b328180000200000043976844");
    dockTag.setDockTagStatus(InstructionStatus.CREATED);
    return dockTag;
  }

  private DockTag getDockTag2() {
    DockTag dockTag = new DockTag();
    dockTag.setCreateUserId("sysadmin");
    dockTag.setCreateTs(new Date());
    dockTag.setDeliveryNumber(12340001L);
    dockTag.setDockTagId("b328180000200000043976845");
    dockTag.setDockTagStatus(InstructionStatus.CREATED);
    return dockTag;
  }

  private List<String> getLPNDockTags(int count) {
    List<String> dockTags = new ArrayList<>();
    String dt = "b32818000020000004397684%s";
    for (int i = 0; i < count; i++) {
      dockTags.add(String.format(dt, i));
    }
    return dockTags;
  }

  private List<Instruction> getInstructions(List<String> dockTags) {
    List<Instruction> instructions = new ArrayList<>();
    for (String dockTag : dockTags) {
      Instruction instruction = new Instruction();
      instruction.setId(RandomUtils.nextLong());
      instruction.setDockTagId(dockTag);
      instructions.add(instruction);
    }
    return instructions;
  }

  private Container getContainer() {
    Container container = new Container();
    container.setDeliveryNumber(12340001L);
    container.setTrackingId("b328180000200000043976844");
    container.setContainerException(ContainerException.DOCK_TAG.getText());
    container.setCreateUser("sysadmin");
    container.setInstructionId(99999L);
    container.setInventoryStatus(InventoryStatus.WORK_IN_PROGRESS.name());
    return container;
  }

  private DockTag getCompletedDockTag() {
    DockTag dockTag = getDockTag();
    dockTag.setDockTagStatus(InstructionStatus.COMPLETED);
    dockTag.setCompleteTs(new Date());
    dockTag.setCompleteUserId("sysadmin");
    return dockTag;
  }

  private String getJSONStringResponse(String path) {
    String payload = null;
    try {
      String filePath = new File(path).getCanonicalPath();
      payload = new String(Files.readAllBytes(Paths.get(filePath)));
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (Objects.nonNull(payload)) {
      return payload;
    }
    return null;
  }
}
