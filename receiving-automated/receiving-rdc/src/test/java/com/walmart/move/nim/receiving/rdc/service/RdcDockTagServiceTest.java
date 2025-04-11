package com.walmart.move.nim.receiving.rdc.service;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.app.TenantSpecificReportConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.PublishInstructionSummary;
import com.walmart.move.nim.receiving.core.model.docktag.*;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelFormat;
import com.walmart.move.nim.receiving.rdc.message.publisher.RdcMessagePublisher;
import com.walmart.move.nim.receiving.rdc.model.docktag.DockTagData;
import com.walmart.move.nim.receiving.rdc.repositories.DockTagCustomRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import io.strati.libs.commons.lang3.RandomUtils;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.CollectionUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcDockTagServiceTest {

  @Mock private PrintJobService printJobService;
  @Mock private TenantSpecificReportConfig tenantSpecificReportConfig;
  @Mock private DockTagPersisterService dockTagPersisterService;
  @Mock private DockTagCustomRepository dockTagCustomRepository;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private InstructionHelperService instructionHelperService;
  @Mock private AppConfig appConfig;
  @Mock private RdcInstructionService rdcInstructionService;
  @Mock private DeliveryServiceRetryableImpl deliveryService;
  @Mock private InventoryService inventoryService;
  @Mock private LPNCacheService lpnCacheService;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private DeliveryMetaDataRepository deliveryMetaDataRepository;
  @Mock private InstructionRepository instructionRepository;
  @Mock private RdcMessagePublisher rdcMessagePublisher;
  @Mock private RdcOsdrService rdcOsdrSummaryService;
  @Mock private ContainerService containerService;

  @Spy @InjectMocks private RdcDockTagService rdcDockTagService;

  private HttpHeaders headers;
  private Gson gson =
      new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  @Captor private ArgumentCaptor<List<Long>> deliveryCaptor;
  @Captor private ArgumentCaptor<List<InstructionStatus>> instructionStatusCaptor;
  @Captor private ArgumentCaptor<List<DockTag>> dockTagCaptor;
  @Captor private ArgumentCaptor<HttpHeaders> httpHeadersCaptor;

  private static final String facilityNum = "32818";
  private static final String countryCode = "US";
  private final String dockTagId = "b328180000200000043976844";

  @BeforeClass
  public void initMocks() throws Exception {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(rdcDockTagService, "gson", gson);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        printJobService,
        tenantSpecificReportConfig,
        dockTagPersisterService,
        dockTagCustomRepository,
        instructionHelperService,
        deliveryService,
        inventoryService,
        lpnCacheService,
        containerPersisterService,
        deliveryMetaDataRepository,
        tenantSpecificConfigReader,
        appConfig,
        instructionRepository,
        rdcInstructionService,
        rdcMessagePublisher,
        rdcOsdrSummaryService,
        rdcDockTagService,
        containerService);
  }

  @Test
  public void testCreateDockTags_withoutTrailerNumberAndCarrierCode() throws ReceivingException {
    List<DockTag> dockTags = new ArrayList<>();
    List<String> dockTagIds = getLPNDockTags(10);
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder().trailerNumber("324242").carrierScacCode("CMC").build();
    when(lpnCacheService.getLPNSBasedOnTenant(10, headers)).thenReturn(dockTagIds);
    when(appConfig.isWftPublishEnabled()).thenReturn(false);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(dockTagPersisterService.saveAllDockTags(anyList())).thenReturn(dockTags);
    when(instructionRepository.saveAll(anyList())).thenReturn(getInstructions(dockTagIds));
    doNothing().when(containerPersisterService).saveContainers(anyList());
    when(printJobService.savePrintJobs(anyList())).thenReturn(new ArrayList<>());
    doNothing()
        .when(rdcInstructionService)
        .publishContainerAndMove(anyString(), any(Container.class), any(HttpHeaders.class));
    doNothing().when(instructionHelperService).publishInstruction(any(), any());
    when(deliveryMetaDataRepository.findDeliveryMetaDataByDeliveryNumber(anyString()))
        .thenReturn(Arrays.asList(deliveryMetaData));
    CreateDockTagRequest createDockTagRequest =
        CreateDockTagRequest.builder()
            .deliveryNumber(12345678L)
            .doorNumber("100")
            .count(10)
            .build();
    DockTagResponse response = rdcDockTagService.createDockTags(createDockTagRequest, headers);
    assertNotNull(response);
    assertNotNull(response.getDockTags());
    assertEquals(response.getDockTags().size(), 10);
    assertEquals(response.getPrintData().getPrintRequests().size(), 10);
    verify(deliveryMetaDataRepository, times(1)).findDeliveryMetaDataByDeliveryNumber(anyString());
    verify(instructionRepository, times(1)).saveAll(anyList());
    verify(containerPersisterService, times(1)).saveContainers(anyList());
    verify(printJobService, times(1)).savePrintJobs(anyList());
    verify(rdcInstructionService, times(10))
        .publishContainerAndMove(anyString(), any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NEW_DOCKTAG_LABEL_FORMAT_ENABLED,
            false);
  }

  @Test
  public void testCreateDockTags_withTrailerNumberAndCarrierCode() throws ReceivingException {
    List<DockTag> dockTags = new ArrayList<>();
    List<String> dockTagIds = getLPNDockTags(10);
    when(lpnCacheService.getLPNSBasedOnTenant(10, headers)).thenReturn(dockTagIds);
    when(appConfig.isWftPublishEnabled()).thenReturn(false);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(dockTagPersisterService.saveAllDockTags(anyList())).thenReturn(dockTags);
    when(instructionRepository.saveAll(anyList())).thenReturn(getInstructions(dockTagIds));
    doNothing().when(containerPersisterService).saveContainers(anyList());
    when(printJobService.savePrintJobs(anyList())).thenReturn(new ArrayList<>());
    doNothing()
        .when(rdcInstructionService)
        .publishContainerAndMove(anyString(), any(Container.class), any(HttpHeaders.class));
    doNothing().when(instructionHelperService).publishInstruction(any(), any());
    CreateDockTagRequest createDockTagRequest =
        CreateDockTagRequest.builder()
            .deliveryNumber(12345678L)
            .doorNumber("100")
            .trailerNumber("344244")
            .carrier("CMC")
            .count(10)
            .build();
    DockTagResponse response = rdcDockTagService.createDockTags(createDockTagRequest, headers);
    assertNotNull(response);
    assertNotNull(response.getDockTags());
    assertEquals(response.getDockTags().size(), 10);
    assertEquals(response.getPrintData().getPrintRequests().size(), 10);

    verify(deliveryMetaDataRepository, times(0)).findDeliveryMetaDataByDeliveryNumber(anyString());
    verify(instructionRepository, times(1)).saveAll(anyList());
    verify(containerPersisterService, times(1)).saveContainers(anyList());
    verify(printJobService, times(1)).savePrintJobs(anyList());
    verify(rdcInstructionService, times(10))
        .publishContainerAndMove(anyString(), any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NEW_DOCKTAG_LABEL_FORMAT_ENABLED,
            false);
  }

  @Test
  public void testCreateDockTags_WFT_Enabled() throws ReceivingException {
    List<DockTag> dockTags = new ArrayList<>();
    List<String> dockTagIds = getLPNDockTags(10);
    when(lpnCacheService.getLPNSBasedOnTenant(10, headers)).thenReturn(dockTagIds);
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(dockTagPersisterService.saveAllDockTags(anyList())).thenReturn(dockTags);
    when(instructionRepository.saveAll(anyList())).thenReturn(getInstructions(dockTagIds));
    doNothing().when(containerPersisterService).saveContainers(anyList());
    when(printJobService.savePrintJobs(anyList())).thenReturn(new ArrayList<>());
    doNothing()
        .when(rdcInstructionService)
        .publishContainerAndMove(anyString(), any(Container.class), any(HttpHeaders.class));
    doNothing().when(instructionHelperService).publishInstruction(any(), any());
    CreateDockTagRequest createDockTagRequest =
        CreateDockTagRequest.builder()
            .deliveryNumber(12345678L)
            .doorNumber("100")
            .count(10)
            .build();
    DockTagResponse response = rdcDockTagService.createDockTags(createDockTagRequest, headers);
    assertNotNull(response);
    assertNotNull(response.getDockTags());
    assertEquals(response.getDockTags().size(), 10);
    assertEquals(response.getPrintData().getPrintRequests().size(), 10);

    List<PrintLabelRequest> printLabelRequests = response.getPrintData().getPrintRequests();
    assertEquals(printLabelRequests.size(), 10);
    assertEquals(printLabelRequests.get(0).getFormatName(), LabelFormat.DOCK_TAG.getFormat());
    assertFalse(printLabelRequests.get(0).getData().isEmpty());

    List<LabelData> containerTagId =
        printLabelRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_CONTAINER_ID))
            .collect(Collectors.toList());
    assertEquals(containerTagId.get(0).getKey(), LabelConstants.LBL_CONTAINER_ID);

    List<LabelData> containerCreationTime =
        printLabelRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_CONTAINER_CREATION_TIME))
            .collect(Collectors.toList());
    assertEquals(containerCreationTime.get(0).getKey(), LabelConstants.LBL_CONTAINER_CREATION_TIME);

    verify(instructionRepository, times(1)).saveAll(anyList());
    verify(containerPersisterService, times(1)).saveContainers(anyList());
    verify(printJobService, times(1)).savePrintJobs(anyList());
    verify(rdcInstructionService, times(10))
        .publishContainerAndMove(anyString(), any(Container.class), any(HttpHeaders.class));
    verify(instructionHelperService, times(1)).publishInstruction(any(), any());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NEW_DOCKTAG_LABEL_FORMAT_ENABLED,
            false);
  }

  @Test
  public void testCreateDockTags_No_Count_Provided() throws ReceivingException {
    List<DockTag> dockTags = new ArrayList<>();
    List<String> dockTagIds = getLPNDockTags(1);
    when(lpnCacheService.getLPNSBasedOnTenant(1, headers)).thenReturn(dockTagIds);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(dockTagPersisterService.saveAllDockTags(anyList())).thenReturn(dockTags);
    when(instructionRepository.saveAll(anyList())).thenReturn(getInstructions(dockTagIds));
    doNothing().when(containerPersisterService).saveContainers(anyList());
    when(printJobService.savePrintJobs(anyList())).thenReturn(new ArrayList<>());
    doNothing()
        .when(rdcInstructionService)
        .publishContainerAndMove(anyString(), any(Container.class), any(HttpHeaders.class));
    doNothing().when(instructionHelperService).publishInstruction(any(), any());
    CreateDockTagRequest createDockTagRequest =
        CreateDockTagRequest.builder()
            .deliveryNumber(12345678L)
            .doorNumber("100")
            .count(null)
            .build();
    DockTagResponse response = rdcDockTagService.createDockTags(createDockTagRequest, headers);
    assertNotNull(response);
    assertNotNull(response.getDockTags());
    assertEquals(response.getDockTags().size(), 1);
    assertEquals(response.getPrintData().getPrintRequests().size(), 1);

    verify(instructionRepository, times(1)).saveAll(anyList());
    verify(containerPersisterService, times(1)).saveContainers(anyList());
    verify(printJobService, times(1)).savePrintJobs(anyList());
    verify(rdcInstructionService, times(1))
        .publishContainerAndMove(anyString(), any(Container.class), any(HttpHeaders.class));
    verify(instructionHelperService, times(1)).publishInstruction(any(), any());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NEW_DOCKTAG_LABEL_FORMAT_ENABLED,
            false);
  }

  @Test
  public void testCreateDockTagsWithRdcDockTagLabelFormat() throws ReceivingException {
    List<DockTag> dockTags = new ArrayList<>();
    List<String> dockTagIds = getLPNDockTags(1);
    when(lpnCacheService.getLPNSBasedOnTenant(1, headers)).thenReturn(dockTagIds);
    when(appConfig.isWftPublishEnabled()).thenReturn(false);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NEW_DOCKTAG_LABEL_FORMAT_ENABLED,
            false))
        .thenReturn(true);
    when(dockTagPersisterService.saveAllDockTags(anyList())).thenReturn(dockTags);
    when(instructionRepository.saveAll(anyList())).thenReturn(getInstructions(dockTagIds));
    doNothing().when(containerPersisterService).saveContainers(anyList());
    when(printJobService.savePrintJobs(anyList())).thenReturn(new ArrayList<>());
    doNothing()
        .when(rdcInstructionService)
        .publishContainerAndMove(anyString(), any(Container.class), any(HttpHeaders.class));
    doNothing().when(instructionHelperService).publishInstruction(any(), any());
    CreateDockTagRequest createDockTagRequest =
        CreateDockTagRequest.builder()
            .deliveryNumber(12345678L)
            .doorNumber("100")
            .trailerNumber("344244")
            .carrier("CMC")
            .count(1)
            .deliveryTypeCode("LIVE")
            .freightType("DA")
            .build();
    DockTagResponse response = rdcDockTagService.createDockTags(createDockTagRequest, headers);
    assertNotNull(response);
    assertNotNull(response.getDockTags());
    assertEquals(response.getDockTags().size(), 1);

    List<PrintLabelRequest> printLabelRequests = response.getPrintData().getPrintRequests();
    assertEquals(printLabelRequests.size(), 1);
    assertEquals(printLabelRequests.get(0).getFormatName(), LabelFormat.NEW_DOCKTAG.getFormat());
    assertFalse(printLabelRequests.get(0).getData().isEmpty());

    List<LabelData> labelDataForDeliveryType =
        printLabelRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_DELIVERY_TYPE))
            .collect(Collectors.toList());
    assertEquals(labelDataForDeliveryType.get(0).getKey(), LabelConstants.LBL_DELIVERY_TYPE);
    assertEquals(labelDataForDeliveryType.get(0).getValue(), "LIVE");

    List<LabelData> labelDataForFreightType =
        printLabelRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_FREIGHT_TYPE))
            .collect(Collectors.toList());
    assertEquals(labelDataForFreightType.get(0).getKey(), LabelConstants.LBL_FREIGHT_TYPE);
    assertEquals(labelDataForFreightType.get(0).getValue(), "DA");

    List<LabelData> labelDataForLabelDate =
        printLabelRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_LBLDATE))
            .collect(Collectors.toList());
    assertEquals(labelDataForLabelDate.get(0).getKey(), LabelConstants.LBL_LBLDATE);

    List<LabelData> labelDataForLabelTime =
        printLabelRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_LBLTIME))
            .collect(Collectors.toList());
    assertEquals(labelDataForLabelTime.get(0).getKey(), LabelConstants.LBL_LBLTIME);

    List<LabelData> containerTagId =
        printLabelRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_CONTAINER_ID))
            .collect(Collectors.toList());
    assertEquals(containerTagId.get(0).getKey(), LabelConstants.LBL_CONTAINER_ID);

    List<LabelData> containerCreationTime =
        printLabelRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_CONTAINER_CREATION_TIME))
            .collect(Collectors.toList());
    assertEquals(containerCreationTime.get(0).getKey(), LabelConstants.LBL_CONTAINER_CREATION_TIME);

    verify(deliveryMetaDataRepository, times(0)).findDeliveryMetaDataByDeliveryNumber(anyString());
    verify(instructionRepository, times(1)).saveAll(anyList());

    ArgumentCaptor<List<Container>> containerArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(containerPersisterService, times(1)).saveContainers(containerArgumentCaptor.capture());
    assertTrue(!containerArgumentCaptor.getValue().get(0).getContainerMiscInfo().isEmpty());
    assertTrue(
        containerArgumentCaptor
            .getValue()
            .get(0)
            .getContainerMiscInfo()
            .containsKey(ReceivingConstants.DELIVERY_TYPE_CODE));
    assertTrue(
        containerArgumentCaptor
            .getValue()
            .get(0)
            .getContainerMiscInfo()
            .containsKey(ReceivingConstants.FREIGHT_TYPE));

    verify(printJobService, times(1)).savePrintJobs(anyList());
    verify(rdcInstructionService, times(1))
        .publishContainerAndMove(anyString(), any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NEW_DOCKTAG_LABEL_FORMAT_ENABLED,
            false);
  }

  @Test
  public void testCreateDockTagsWithRdcDockTagLabelFormatTrimDeliveryTypeAndFreightType()
      throws ReceivingException {
    List<DockTag> dockTags = new ArrayList<>();
    List<String> dockTagIds = getLPNDockTags(1);
    when(lpnCacheService.getLPNSBasedOnTenant(1, headers)).thenReturn(dockTagIds);
    when(appConfig.isWftPublishEnabled()).thenReturn(false);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NEW_DOCKTAG_LABEL_FORMAT_ENABLED,
            false))
        .thenReturn(true);
    when(dockTagPersisterService.saveAllDockTags(anyList())).thenReturn(dockTags);
    when(instructionRepository.saveAll(anyList())).thenReturn(getInstructions(dockTagIds));
    doNothing().when(containerPersisterService).saveContainers(anyList());
    when(printJobService.savePrintJobs(anyList())).thenReturn(new ArrayList<>());
    doNothing()
        .when(rdcInstructionService)
        .publishContainerAndMove(anyString(), any(Container.class), any(HttpHeaders.class));
    doNothing().when(instructionHelperService).publishInstruction(any(), any());
    CreateDockTagRequest createDockTagRequest =
        CreateDockTagRequest.builder()
            .deliveryNumber(12345678L)
            .doorNumber("100")
            .trailerNumber("344244")
            .carrier("CMC")
            .count(1)
            .deliveryTypeCode("APPOINTMENT")
            .freightType("SSTKU")
            .build();
    DockTagResponse response = rdcDockTagService.createDockTags(createDockTagRequest, headers);
    assertNotNull(response);
    assertNotNull(response.getDockTags());
    assertEquals(response.getDockTags().size(), 1);

    List<PrintLabelRequest> printLabelRequests = response.getPrintData().getPrintRequests();
    assertEquals(printLabelRequests.size(), 1);
    assertEquals(printLabelRequests.get(0).getFormatName(), LabelFormat.NEW_DOCKTAG.getFormat());
    assertFalse(printLabelRequests.get(0).getData().isEmpty());

    List<LabelData> labelDataForDeliveryType =
        printLabelRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_DELIVERY_TYPE))
            .collect(Collectors.toList());
    assertEquals(labelDataForDeliveryType.get(0).getKey(), LabelConstants.LBL_DELIVERY_TYPE);
    assertEquals(labelDataForDeliveryType.get(0).getValue(), "APPOIN");

    List<LabelData> labelDataForFreightType =
        printLabelRequests
            .get(0)
            .getData()
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_FREIGHT_TYPE))
            .collect(Collectors.toList());
    assertEquals(labelDataForFreightType.get(0).getKey(), LabelConstants.LBL_FREIGHT_TYPE);
    assertEquals(labelDataForFreightType.get(0).getValue(), "SSTKU");

    verify(deliveryMetaDataRepository, times(0)).findDeliveryMetaDataByDeliveryNumber(anyString());
    verify(instructionRepository, times(1)).saveAll(anyList());
    verify(containerPersisterService, times(1)).saveContainers(anyList());
    verify(printJobService, times(1)).savePrintJobs(anyList());
    verify(rdcInstructionService, times(1))
        .publishContainerAndMove(anyString(), any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NEW_DOCKTAG_LABEL_FORMAT_ENABLED,
            false);
  }

  @Test
  public void testSearchDockTag_By_DockTag() {
    Long date = null;
    Long deliveryNum = null;
    String dockTagId = "b328180000200000043976844";
    List<DockTagData> dockTagDataList =
        Arrays.asList(
            DockTagData.builder()
                .deliveryNumber(12345678L)
                .dockTagId("b328180000200000043976844")
                .build());
    when(dockTagCustomRepository.searchDockTags(any(), any(), any(), any(), any(), any()))
        .thenReturn(dockTagDataList);
    List<DockTagData> response =
        rdcDockTagService.searchDockTag(
            Optional.ofNullable(dockTagId),
            Optional.ofNullable(deliveryNum),
            Optional.ofNullable(date),
            Optional.ofNullable(date));
    assertNotNull(response);
    assertEquals(response.size(), 1);
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void testSearchDockTag_By_Invalid_DockTag() {
    Long date = null;
    Long deliveryNum = null;
    String dockTagId = "b328180000200000043976844";
    when(dockTagCustomRepository.searchDockTags(any(), any(), any(), any(), any(), any()))
        .thenReturn(null);
    rdcDockTagService.searchDockTag(
        Optional.ofNullable(dockTagId),
        Optional.ofNullable(deliveryNum),
        Optional.ofNullable(date),
        Optional.ofNullable(date));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testSearchDockTag_By_Completed_DockTag() {
    Long date = null;
    Long deliveryNum = null;
    String dockTagId = "b328180000200000043976844";
    List<DockTagData> dockTagDataList =
        Arrays.asList(
            DockTagData.builder()
                .deliveryNumber(12345678L)
                .dockTagId("b328180000200000043976844")
                .completeTs(new Date().getTime())
                .completeUserId("sysadmin")
                .build());
    when(dockTagCustomRepository.searchDockTags(any(), any(), any(), any(), any(), any()))
        .thenReturn(dockTagDataList);
    rdcDockTagService.searchDockTag(
        Optional.ofNullable(dockTagId),
        Optional.ofNullable(deliveryNum),
        Optional.ofNullable(date),
        Optional.ofNullable(date));
  }

  @Test
  public void testSearchDockTag_By_DeliveryNumber() {
    Long date = null;
    Long deliveryNum = 12345678L;
    String dockTagId = null;
    when(dockTagCustomRepository.searchDockTags(any(), any(), any(), any(), any(), any()))
        .thenReturn(getDockTagDBData());
    List<DockTagData> response =
        rdcDockTagService.searchDockTag(
            Optional.ofNullable(dockTagId),
            Optional.ofNullable(deliveryNum),
            Optional.ofNullable(date),
            Optional.ofNullable(date));
    assertNotNull(response);
    assertEquals(response.size(), 3);
  }

  @Test
  public void testSearchDockTag_With_Dates() {
    Long date = System.currentTimeMillis();
    Long deliveryNum = 12345678L;
    String dockTagId = null;
    when(tenantSpecificReportConfig.getDCTimeZone(anyString())).thenReturn("US/Central");
    when(dockTagCustomRepository.searchDockTags(any(), any(), any(), any(), any(), any()))
        .thenReturn(getDockTagDBData());
    List<DockTagData> response =
        rdcDockTagService.searchDockTag(
            Optional.ofNullable(dockTagId),
            Optional.ofNullable(deliveryNum),
            Optional.ofNullable(date),
            Optional.ofNullable(date));
    assertNotNull(response);
    assertEquals(response.size(), 3);
  }

  @Test
  public void testSearchDockTag_AllDockTags() {
    Long date = System.currentTimeMillis();
    Long deliveryNum = 12345678L;
    String dockTagId = null;
    when(tenantSpecificReportConfig.getDCTimeZone(anyString())).thenReturn("US/Central");
    when(dockTagCustomRepository.searchDockTags(any(), any(), any(), any(), any(), any()))
        .thenReturn(getDockTagDBData());
    List<DockTagData> response =
        rdcDockTagService.searchDockTag(
            Optional.ofNullable(dockTagId),
            Optional.ofNullable(deliveryNum),
            Optional.ofNullable(date),
            Optional.ofNullable(date));
    assertNotNull(response);
    assertEquals(response.size(), 3);
  }

  @Test
  public void testCompleteDockTag_HappyFlow_makesCall_toInventory_When_Config_isEnabled() {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(getDockTag());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(0);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Central");
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryNumber(anyLong())).thenReturn(10);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList()))
        .thenReturn(3);
    doNothing().when(rdcMessagePublisher).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
    DockTagData dockTagData =
        rdcDockTagService.completeDockTagById(dockTagId, MockHttpHeaders.getHeaders());

    assertEquals(dockTagData.getStatus(), InstructionStatus.COMPLETED);
    assertEquals(dockTagData.getCreateUserId(), "sysadmin");
    assertNotNull(dockTagData.getCompleteTs());
    assertNotNull(dockTagData.getLastChangedTs());
    assertEquals(dockTagData.getCompleteUserId(), "sysadmin");

    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryService, times(1)).deleteContainer(eq(dockTagId), any());
    verify(dockTagPersisterService, times(1)).getCountOfDockTagsByDeliveryNumber(anyLong());
    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList());
    verify(rdcMessagePublisher, times(1)).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
  }

  @Test
  public void testCompleteDockTag_HappyFlow_notMakes_callToInventory_When_Config_isNotEnabled() {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(getDockTag());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Central");
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryNumber(anyLong())).thenReturn(10);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList()))
        .thenReturn(3);
    doNothing().when(rdcMessagePublisher).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());

    DockTagData dockTagData =
        rdcDockTagService.completeDockTagById(dockTagId, MockHttpHeaders.getHeaders());

    assertEquals(dockTagData.getStatus(), InstructionStatus.COMPLETED);
    assertEquals(dockTagData.getCreateUserId(), "sysadmin");
    assertNotNull(dockTagData.getCompleteTs());
    assertNotNull(dockTagData.getLastChangedTs());
    assertEquals(dockTagData.getCompleteUserId(), "sysadmin");

    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryService, times(0)).deleteContainer(eq(dockTagId), any());
    verify(dockTagPersisterService, times(1)).getCountOfDockTagsByDeliveryNumber(anyLong());
    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList());
    verify(rdcMessagePublisher, times(1)).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
    verify(rdcMessagePublisher, times(0)).publishDeliveryReceipts(any(OsdrSummary.class), anyMap());
  }

  @Test
  public void
      testCompleteDockTag_HappyFlow_notMakes_callToInventory_When_Config_isNotEnabled_LastDockTagToPublishOSDR()
          throws ReceivingException {
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setSummary(Arrays.asList());
    osdrSummary.setAuditPending(false);
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(getDockTag());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Central");
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryNumber(anyLong())).thenReturn(10);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList()))
        .thenReturn(0);
    doNothing().when(rdcMessagePublisher).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
    doNothing().when(rdcMessagePublisher).publishDeliveryReceipts(any(OsdrSummary.class), anyMap());
    when(rdcOsdrSummaryService.getOsdrSummary(anyLong(), any(HttpHeaders.class)))
        .thenReturn(osdrSummary);

    DockTagData dockTagData =
        rdcDockTagService.completeDockTagById(dockTagId, MockHttpHeaders.getHeaders());

    assertEquals(dockTagData.getStatus(), InstructionStatus.COMPLETED);
    assertEquals(dockTagData.getCreateUserId(), "sysadmin");
    assertNotNull(dockTagData.getCompleteTs());
    assertNotNull(dockTagData.getLastChangedTs());
    assertEquals(dockTagData.getCompleteUserId(), "sysadmin");

    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryService, times(0)).deleteContainer(eq(dockTagId), any());
    verify(dockTagPersisterService, times(1)).getCountOfDockTagsByDeliveryNumber(anyLong());
    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList());
    verify(rdcMessagePublisher, times(1)).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
    verify(rdcMessagePublisher, times(1)).publishDeliveryReceipts(any(OsdrSummary.class), anyMap());
    verify(rdcOsdrSummaryService, times(1)).getOsdrSummary(anyLong(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void testCompleteDockTag_DockTagNotFound_ExceptionScenario() {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(null);
    rdcDockTagService.completeDockTagById(dockTagId, MockHttpHeaders.getHeaders());
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testCompleteDockTag_DockTagCompleted_ExceptionScenario() {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(getCompletedDockTag());
    rdcDockTagService.completeDockTagById(dockTagId, MockHttpHeaders.getHeaders());
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testCompleteDockTag_ExceptionFromInventory() {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(getDockTag());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(0);
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.UNABLE_TO_PROCESS_INVENTORY,
                ReceivingConstants.INVENTORY_SERVICE_DOWN))
        .when(inventoryService)
        .deleteContainer(anyString(), any(HttpHeaders.class));
    rdcDockTagService.completeDockTagById(dockTagId, MockHttpHeaders.getHeaders());

    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryService, times(1)).deleteContainer(eq(dockTagId), any());
    verify(dockTagPersisterService, times(0)).getCountOfDockTagsByDeliveryNumber(anyLong());
    verify(dockTagPersisterService, times(0))
        .getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList());
    verify(rdcMessagePublisher, times(0)).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
    verify(rdcMessagePublisher, times(0)).publishDeliveryReceipts(any(OsdrSummary.class), anyMap());
  }

  @Test
  public void testReceiveDockTag_HappyFlow_invoked_from_Atlas() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(getDockTag());
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(getMockDelivery());
    DockTagData receiveDockTagDataResponse = rdcDockTagService.receiveDockTag(dockTagId, headers);
    assertNotNull(receiveDockTagDataResponse);
    assertEquals(receiveDockTagDataResponse.getStatus(), InstructionStatus.CREATED);
    assertNotNull(receiveDockTagDataResponse.getDeliveryInfo());
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(deliveryService, times(1)).getDeliveryByURI(any(URI.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveDockTag_HappyFlow_invoked_from_NGR() throws ReceivingException {
    headers.add(ReceivingConstants.WMT_REQ_SOURCE, "NGR");
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(getDockTag());
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(getMockDelivery());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Central");
    DockTagData receiveDockTagDataResponse = rdcDockTagService.receiveDockTag(dockTagId, headers);
    Assert.assertNotNull(receiveDockTagDataResponse);
    assertEquals(receiveDockTagDataResponse.getStatus(), InstructionStatus.CREATED);
    assertNull(receiveDockTagDataResponse.getDeliveryInfo());
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(deliveryService, times(0)).getDeliveryByURI(any(URI.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveDockTag_DockTagCompleted_ExceptionScenario() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(getCompletedDockTag());
    rdcDockTagService.receiveDockTag(dockTagId, headers);
    verify(dockTagPersisterService, times(0)).getDockTagByDockTagId(eq(dockTagId));
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void testReceiveDockTag_DockTagNotFound_ExceptionScenario() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(null);
    DockTagData receiveDockTagDataResponse = rdcDockTagService.receiveDockTag(dockTagId, headers);
    Assert.assertNull(receiveDockTagDataResponse);
    verify(dockTagPersisterService, times(0)).getDockTagByDockTagId(eq(dockTagId));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testReceiveDockTag_GdmServiceDown_ExceptionScenario() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(getDockTag());
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                ReceivingException.GDM_SERVICE_DOWN,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE));
    DockTagData receiveDockTagDataResponse = rdcDockTagService.receiveDockTag(dockTagId, headers);
    Assert.assertNull(receiveDockTagDataResponse);
    verify(dockTagPersisterService, times(0)).getDockTagByDockTagId(eq(dockTagId));
    verify(deliveryService, times(0)).getDeliveryByURI(any(URI.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testReceiveDockTag_DeliveryNotFoundInGdm_ExceptionScenario()
      throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(getDockTag());
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                ReceivingException.DELIVERY_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE));
    DockTagData receiveDockTagDataResponse = rdcDockTagService.receiveDockTag(dockTagId, headers);
    Assert.assertNull(receiveDockTagDataResponse);
    verify(dockTagPersisterService, times(0)).getDockTagByDockTagId(eq(dockTagId));
    verify(deliveryService, times(0)).getDeliveryByURI(any(URI.class), any(HttpHeaders.class));
  }

  @Test
  public void testCreateContainer() {
    when(containerPersisterService.saveContainer(any(Container.class))).thenReturn(getContainer());

    CreateDockTagRequest createDockTagRequest =
        CreateDockTagRequest.builder().deliveryNumber(12345678L).doorNumber("100").build();

    Container container = rdcDockTagService.getDockTagContainer(createDockTagRequest, headers);

    assertNotNull(container);
    assertTrue(container.getContainerException() == ContainerException.DOCK_TAG.getText());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testCreateDockTag() {
    rdcDockTagService.createDockTag(new CreateDockTagRequest(), MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testUpdateDockTagById() {
    rdcDockTagService.updateDockTagById("dockTagId", InstructionStatus.UPDATED, "sysadmin");
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testCreateDockTag_Params() {
    rdcDockTagService.createDockTag("dockTagId", 1L, "sysadmin", DockTagType.ATLAS_RECEIVING);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testSearchDockTagInvalidDelivery() {
    List<String> deliveryNumbers = Arrays.asList("a12345");
    SearchDockTagRequest searchDockTagRequest =
        SearchDockTagRequest.builder().deliveryNumbers(deliveryNumbers).build();
    rdcDockTagService.searchDockTag(searchDockTagRequest, null);
  }

  @Test
  public void testSearchDockTag() {
    List<String> deliveryNumbers = Arrays.asList("1234567");
    SearchDockTagRequest searchDockTagRequest =
        SearchDockTagRequest.builder().deliveryNumbers(deliveryNumbers).build();
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(getDockTag());
    dockTagList.add(getCompletedDockTag());
    when(dockTagPersisterService.getDockTagsByDeliveries(anyList())).thenReturn(dockTagList);

    assertEquals(
        rdcDockTagService.searchDockTag(searchDockTagRequest, null), gson.toJson(dockTagList));

    verify(dockTagPersisterService, times(1)).getDockTagsByDeliveries(deliveryCaptor.capture());
    assertEquals(deliveryCaptor.getValue().get(0).longValue(), 1234567L);
  }

  @Test
  public void testSearchDockTagWithCreatedStatus() {
    List<String> deliveryNumbers = Arrays.asList("1234567");
    SearchDockTagRequest searchDockTagRequest =
        SearchDockTagRequest.builder().deliveryNumbers(deliveryNumbers).build();
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(getDockTag());
    when(dockTagPersisterService.getDockTagsByDeliveriesAndStatuses(anyList(), anyList()))
        .thenReturn(dockTagList);

    assertEquals(
        rdcDockTagService.searchDockTag(searchDockTagRequest, InstructionStatus.CREATED),
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
    dockTagList.add(getCompletedDockTag());
    when(dockTagPersisterService.getDockTagsByDeliveriesAndStatuses(anyList(), anyList()))
        .thenReturn(dockTagList);

    assertEquals(
        rdcDockTagService.searchDockTag(searchDockTagRequest, InstructionStatus.COMPLETED),
        gson.toJson(dockTagList));

    verify(dockTagPersisterService, times(1))
        .getDockTagsByDeliveriesAndStatuses(
            deliveryCaptor.capture(), instructionStatusCaptor.capture());
    assertEquals(deliveryCaptor.getValue().get(0).longValue(), 1234567L);
    assertTrue(instructionStatusCaptor.getValue().get(0).equals(InstructionStatus.COMPLETED));
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
        rdcDockTagService.completeDockTags(completeDockTagRequest, httpHeaders);
    assertNotNull(completeDockTagResponse.getFailed());
    assertTrue(CollectionUtils.isEmpty(completeDockTagResponse.getSuccess()));
  }

  @Test
  public void testCompleteBulkDockTags_AllDockTagsExists_Success() {
    DockTag dockTag = getDockTag();
    DockTag dockTag2 = getDockTag2();
    List<String> docktags = Arrays.asList(dockTag.getDockTagId(), dockTag2.getDockTagId());
    CompleteDockTagRequest completeDockTagRequest =
        CompleteDockTagRequest.builder()
            .deliveryNumber(1234567L)
            .deliveryStatus("OPN")
            .docktags(docktags)
            .build();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(dockTag);
    dockTagList.add(dockTag2);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryNumber(anyLong())).thenReturn(10);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList()))
        .thenReturn(3);
    doNothing().when(rdcMessagePublisher).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());

    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(dockTagList);

    CompleteDockTagResponse completeDockTagResponse =
        rdcDockTagService.completeDockTags(completeDockTagRequest, httpHeaders);
    assertTrue(CollectionUtils.isEmpty(completeDockTagResponse.getFailed()));
    assertNotNull(completeDockTagResponse.getSuccess());
    assertEquals(completeDockTagResponse.getSuccess().size(), 2);
    verify(inventoryService, times(dockTagList.size()))
        .deleteContainer(anyString(), any(HttpHeaders.class));
    verify(dockTagPersisterService, times(2)).saveDockTag(any(DockTag.class));
    verify(dockTagPersisterService, times(1)).getCountOfDockTagsByDeliveryNumber(anyLong());
    verify(dockTagPersisterService, times(2))
        .getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList());
    verify(rdcMessagePublisher, times(2)).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
  }

  @Test
  public void testCompleteBulkDockTags_InventoryIntegration_Disabled() {
    DockTag dockTag = getDockTag();
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
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(dockTagList);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryNumber(anyLong())).thenReturn(10);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList()))
        .thenReturn(0);
    doNothing().when(rdcMessagePublisher).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());

    CompleteDockTagResponse completeDockTagResponse =
        rdcDockTagService.completeDockTags(completeDockTagRequest, httpHeaders);
    assertTrue(CollectionUtils.isEmpty(completeDockTagResponse.getFailed()));
    assertNotNull(completeDockTagResponse.getSuccess());
    assertEquals(completeDockTagResponse.getSuccess().size(), 1);
    verify(inventoryService, times(0)).deleteContainer(dockTag.getDockTagId(), httpHeaders);
    verify(dockTagPersisterService, times(1)).saveDockTag(any(DockTag.class));
    verify(dockTagPersisterService, times(1)).getCountOfDockTagsByDeliveryNumber(anyLong());
    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList());
    verify(rdcMessagePublisher, times(1)).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
  }

  @Test
  public void testCompleteBulkDockTags_PartiallyAvailableInDB() {
    DockTag dockTag = getDockTag();
    DockTag completeDockTag = getCompletedDockTag();
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
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(dockTagList);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryNumber(anyLong())).thenReturn(10);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList()))
        .thenReturn(0);
    doNothing().when(rdcMessagePublisher).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
    CompleteDockTagResponse completeDockTagResponse =
        rdcDockTagService.completeDockTags(completeDockTagRequest, httpHeaders);

    assertEquals(completeDockTagResponse.getFailed().get(0), completeDockTag.getDockTagId());
    assertEquals(completeDockTagResponse.getSuccess().get(0), dockTag.getDockTagId());
    verify(inventoryService, times(1)).deleteContainer(anyString(), any(HttpHeaders.class));
    verify(dockTagPersisterService, times(1)).saveDockTag(any(DockTag.class));
    verify(dockTagPersisterService, times(1)).getCountOfDockTagsByDeliveryNumber(anyLong());
    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList());
    verify(rdcMessagePublisher, times(1)).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
  }

  @Test
  public void testCompleteBulkDockTags_InventoryException_For_Partial_DockTags() {
    DockTag dockTag = getDockTag();
    DockTag dockTag1 = getDockTag();
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
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryNumber(anyLong())).thenReturn(10);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList()))
        .thenReturn(0);
    doNothing().when(rdcMessagePublisher).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(dockTagList);
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.UNABLE_TO_PROCESS_INVENTORY,
                ReceivingConstants.INVENTORY_SERVICE_DOWN))
        .when(inventoryService)
        .deleteContainer(anyString(), any(HttpHeaders.class));

    CompleteDockTagResponse completeDockTagResponse =
        rdcDockTagService.completeDockTags(completeDockTagRequest, httpHeaders);

    assertTrue(completeDockTagResponse.getFailed().contains(dockTag1.getDockTagId()));
    assertTrue(completeDockTagResponse.getFailed().contains(dockTag.getDockTagId()));
    assertEquals(completeDockTagResponse.getSuccess().size(), 0);
    assertEquals(completeDockTagResponse.getFailed().size(), 2);

    verify(inventoryService, times(2)).deleteContainer(anyString(), any(HttpHeaders.class));
    verify(dockTagPersisterService, times(0)).saveDockTag(any(DockTag.class));
    verify(dockTagPersisterService, times(1)).getCountOfDockTagsByDeliveryNumber(anyLong());
    verify(dockTagPersisterService, times(0))
        .getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList());
    verify(rdcMessagePublisher, times(0)).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
  }

  @Test
  public void testCompleteBulkDockTags_InventoryException_For_AllDockTags() {
    DockTag dockTag = getDockTag();
    DockTag dockTag1 = getDockTag();
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
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryNumber(anyLong())).thenReturn(10);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList()))
        .thenReturn(0);
    doNothing().when(rdcMessagePublisher).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(dockTagList);
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.UNABLE_TO_PROCESS_INVENTORY,
                ReceivingConstants.INVENTORY_SERVICE_DOWN))
        .when(inventoryService)
        .deleteContainer(anyString(), any(HttpHeaders.class));

    CompleteDockTagResponse completeDockTagResponse =
        rdcDockTagService.completeDockTags(completeDockTagRequest, httpHeaders);

    assertEquals(completeDockTagResponse.getSuccess().size(), 0);
    assertEquals(completeDockTagResponse.getFailed().size(), 2);

    verify(inventoryService, times(2)).deleteContainer(anyString(), any(HttpHeaders.class));
    verify(dockTagPersisterService, times(0)).saveDockTag(any(DockTag.class));
    verify(dockTagPersisterService, times(1)).getCountOfDockTagsByDeliveryNumber(anyLong());
    verify(dockTagPersisterService, times(0))
        .getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList());
    verify(rdcMessagePublisher, times(0)).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
  }

  @Test
  public void testMarkCompleteAndDeleteFromInventoryIsSuccess() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(inventoryService).deleteContainer(anyString(), any(HttpHeaders.class));
    rdcDockTagService.markCompleteAndDeleteFromInventory(headers, getDockTag());

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryService, times(1)).deleteContainer(anyString(), httpHeadersCaptor.capture());

    assertTrue(httpHeadersCaptor.getValue().containsKey(ReceivingConstants.REQUEST_ORIGINATOR));
    assertSame(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.REQUEST_ORIGINATOR),
        ReceivingConstants.APP_NAME_VALUE);
  }

  @Test
  public void testAutoCompleteDockTagNoPendingDockTag() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -72);
    Date fromDate = cal.getTime();
    when(dockTagPersisterService.getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), any(), any()))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getCcmConfigValue(anyString(), anyString()))
        .thenReturn(gson.toJsonTree(Integer.valueOf(72)));
    doReturn(new DockTagData())
        .when(rdcDockTagService)
        .completeDockTagById(anyString(), any(HttpHeaders.class));

    rdcDockTagService.autoCompleteDocks(48, 10);

    ArgumentCaptor<Date> dateCaptor = ArgumentCaptor.forClass(Date.class);
    verify(tenantSpecificConfigReader, times(1)).getCcmConfigValue(anyString(), anyString());
    verify(dockTagPersisterService, times(1))
        .getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), dateCaptor.capture(), any());
    assertTrue(dateCaptor.getValue().getDate() == fromDate.getDate());
  }

  @Test
  public void testAutoCompleteDockTagNoPendingDockTagDefaultHours() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -72);
    Date fromDate = cal.getTime();
    when(dockTagPersisterService.getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), any(), any()))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getCcmConfigValue(anyString(), anyString())).thenReturn(null);
    doReturn(new DockTagData())
        .when(rdcDockTagService)
        .completeDockTagById(anyString(), any(HttpHeaders.class));

    rdcDockTagService.autoCompleteDocks(48, 10);

    ArgumentCaptor<Date> dateCaptor = ArgumentCaptor.forClass(Date.class);
    verify(tenantSpecificConfigReader, times(1)).getCcmConfigValue(anyString(), anyString());
    verify(dockTagPersisterService, times(1))
        .getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), dateCaptor.capture(), any());
    assertTrue(dateCaptor.getValue().getDate() == fromDate.getDate());
  }

  @Test
  public void testAutoCompleteDockTagNoPendingDockTagEmptyList() {
    when(dockTagPersisterService.getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), any(), any()))
        .thenReturn(new ArrayList<>());
    when(tenantSpecificConfigReader.getCcmConfigValue(anyString(), anyString()))
        .thenReturn(gson.toJsonTree(Integer.valueOf(72)));

    rdcDockTagService.autoCompleteDocks(48, 10);
    verify(tenantSpecificConfigReader, times(1)).getCcmConfigValue(anyString(), anyString());
    verify(dockTagPersisterService, times(1))
        .getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), any(), any());
  }

  @Test
  public void testAutoCompleteDockTag() {
    List<DockTag> dockTagList = new ArrayList<>();
    DockTag dockTag1 = getDockTag();
    DockTag dockTag2 = getDockTag2();
    DockTag dockTag3 = getDockTag();
    dockTag3.setDeliveryNumber(23456769l);
    dockTagList.add(dockTag1);
    dockTagList.add(dockTag2);
    dockTagList.add(dockTag3);
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -72);
    Date fromDate = cal.getTime();
    when(dockTagPersisterService.getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), any(), any()))
        .thenReturn(dockTagList);
    when(tenantSpecificConfigReader.getCcmConfigValue(anyString(), anyString()))
        .thenReturn(gson.toJsonTree(Integer.valueOf(72)));
    doReturn(new DockTagData())
        .when(rdcDockTagService)
        .completeDockTagById(anyString(), any(HttpHeaders.class));

    rdcDockTagService.autoCompleteDocks(48, 10);

    ArgumentCaptor<Date> dateCaptor = ArgumentCaptor.forClass(Date.class);
    verify(tenantSpecificConfigReader, times(1)).getCcmConfigValue(anyString(), anyString());
    verify(dockTagPersisterService, times(1))
        .getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), dateCaptor.capture(), any());
    verify(rdcDockTagService, times(3)).completeDockTagById(anyString(), any(HttpHeaders.class));
    assertTrue(dateCaptor.getValue().getDate() == fromDate.getDate());
  }

  @Test
  public void testAutoCompleteDockTag_AllDockTagsNotCompleted() {
    List<DockTag> dockTagList = new ArrayList<>();
    DockTag dockTag1 = getDockTag();
    DockTag dockTag2 = getDockTag2();
    DockTag dockTag3 = getDockTag();
    dockTagList.add(dockTag1);
    dockTagList.add(dockTag2);
    dockTagList.add(dockTag3);
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -72);
    Date fromDate = cal.getTime();
    when(dockTagPersisterService.getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), any(), any()))
        .thenReturn(dockTagList);
    when(tenantSpecificConfigReader.getCcmConfigValue(anyString(), anyString()))
        .thenReturn(gson.toJsonTree(Integer.valueOf(72)));
    doReturn(new DockTagData())
        .when(rdcDockTagService)
        .completeDockTagById(dockTag1.getDockTagId(), ReceivingUtils.getHeaders());
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.UNABLE_TO_COMPLETE_DOCKTAG,
                String.format(
                    ReceivingConstants.UNABLE_TO_COMPLETE_DOCKTAG,
                    dockTag2.getDockTagId(),
                    dockTag2.getDockTagId())))
        .when(rdcDockTagService)
        .completeDockTagById(dockTag2.getDockTagId(), ReceivingUtils.getHeaders());

    rdcDockTagService.autoCompleteDocks(48, 10);

    ArgumentCaptor<Date> dateCaptor = ArgumentCaptor.forClass(Date.class);
    verify(tenantSpecificConfigReader, times(1)).getCcmConfigValue(anyString(), anyString());
    verify(dockTagPersisterService, times(1))
        .getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), dateCaptor.capture(), any());
    verify(rdcDockTagService, times(3)).completeDockTagById(anyString(), any(HttpHeaders.class));
    assertTrue(dateCaptor.getValue().getDate() == fromDate.getDate());
  }

  @Test
  private void testPartialDockTagCompleteApi_ThrowsException_WhenRetryFlag_Is_True()
      throws ReceivingException {
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.LPNS_NOT_FOUND, ReceivingConstants.LPNS_NOT_FOUND))
        .when(lpnCacheService)
        .getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class));

    CreateDockTagRequest createDockTagRequest = getCreateDockTagRequest();

    try {
      DockTagResponse response =
          rdcDockTagService.partialCompleteDockTag(
              createDockTagRequest, dockTagId, true, MockHttpHeaders.getHeaders());
    } catch (ReceivingInternalException excp) {
      assertNotNull(excp);
      assertTrue(excp.getErrorCode().equals(ExceptionCodes.LPNS_NOT_FOUND));
    }
    verify(lpnCacheService, times(1)).getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class));
  }

  @Test
  private void testPartialDockTagCompleteApi_IsSuccess_WhenRetryFlag_Is_True()
      throws ReceivingException {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NEW_DOCKTAG_LABEL_FORMAT_ENABLED,
            false))
        .thenReturn(true);
    when(containerService.findByTrackingId(anyString())).thenReturn(getContainer());
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(getDockTag());
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("b328180000200000043976844"));
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(dockTagPersisterService.saveAllDockTags(anyList()))
        .thenReturn(Collections.singletonList(getDockTag()));
    when(instructionRepository.saveAll(anyList()))
        .thenReturn(getInstructions(Arrays.asList("b328180000200000043976844")));
    doNothing().when(containerPersisterService).saveContainers(anyList());
    when(printJobService.savePrintJobs(anyList())).thenReturn(new ArrayList<>());
    doNothing()
        .when(rdcInstructionService)
        .publishContainerAndMove(anyString(), any(Container.class), any(HttpHeaders.class));
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryNumber(anyLong())).thenReturn(3);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList()))
        .thenReturn(3);
    doNothing().when(rdcMessagePublisher).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());

    CreateDockTagRequest createDockTagRequest = getCreateDockTagRequest();
    createDockTagRequest.setDeliveryTypeCode("DROP");
    DockTagResponse response =
        rdcDockTagService.partialCompleteDockTag(
            createDockTagRequest, dockTagId, true, MockHttpHeaders.getHeaders());

    assertNotNull(response);
    assertNotNull(response.getDockTags());
    assertEquals(response.getDockTags().size(), 1);
    assertEquals(response.getPrintData().getPrintRequests().size(), 1);

    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NEW_DOCKTAG_LABEL_FORMAT_ENABLED,
            false);
    verify(containerService, times(1)).findByTrackingId(anyString());
    verify(lpnCacheService, times(1)).getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class));
    verify(instructionRepository, times(1)).saveAll(anyList());

    ArgumentCaptor<List<Container>> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(containerPersisterService, times(1)).saveContainers(listArgumentCaptor.capture());
    assertTrue(listArgumentCaptor.getValue().get(0).getContainerMiscInfo().size() > 0);
    assertTrue(
        listArgumentCaptor
            .getValue()
            .get(0)
            .getContainerMiscInfo()
            .containsKey(ReceivingConstants.DELIVERY_TYPE_CODE));
    assertTrue(
        listArgumentCaptor
            .getValue()
            .get(0)
            .getContainerMiscInfo()
            .containsKey(ReceivingConstants.FREIGHT_TYPE));

    verify(printJobService, times(1)).savePrintJobs(anyList());
    verify(rdcInstructionService, times(1))
        .publishContainerAndMove(anyString(), any(Container.class), any(HttpHeaders.class));
    verify(dockTagPersisterService, times(1)).getCountOfDockTagsByDeliveryNumber(anyLong());
    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList());
    verify(rdcMessagePublisher, times(1)).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
  }

  @Test
  private void testPartialDockTagCompleteApi_IsSuccess_WhenRetryFlag_Is_False()
      throws ReceivingException {
    // Complete mocks
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(getDockTag());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    doNothing().when(dockTagPersisterService).saveDockTag(any(DockTag.class));

    // Create mocks
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("b328180000200000043976845"));
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NEW_DOCKTAG_LABEL_FORMAT_ENABLED,
            false))
        .thenReturn(true);
    when(containerService.findByTrackingId(anyString())).thenReturn(getContainer());
    when(dockTagPersisterService.saveAllDockTags(anyList()))
        .thenReturn(Collections.singletonList(getDockTag()));
    when(instructionRepository.saveAll(anyList()))
        .thenReturn(getInstructions(Arrays.asList("b328180000200000043976845")));
    doNothing().when(containerPersisterService).saveContainers(anyList());
    when(printJobService.savePrintJobs(anyList())).thenReturn(new ArrayList<>());
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    doNothing()
        .when(rdcInstructionService)
        .publishContainerAndMove(anyString(), any(Container.class), any(HttpHeaders.class));
    doNothing().when(instructionHelperService).publishInstruction(any(), any());
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryNumber(anyLong())).thenReturn(3);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList()))
        .thenReturn(3);
    doNothing().when(rdcMessagePublisher).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());

    CreateDockTagRequest createDockTagRequest = getCreateDockTagRequest();
    createDockTagRequest.setDeliveryTypeCode("DROP");
    createDockTagRequest.setFreightType("DA");
    DockTagResponse response =
        rdcDockTagService.partialCompleteDockTag(
            createDockTagRequest, dockTagId, false, MockHttpHeaders.getHeaders());

    assertNotNull(response);
    assertEquals(response.getDockTags().size(), 1);
    assertNotNull(response.getPrintData());

    // complete verify mocks
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(inventoryService, times(1)).deleteContainer(eq(dockTagId), any());
    verify(dockTagPersisterService, times(1)).getCountOfDockTagsByDeliveryNumber(anyLong());
    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList());
    verify(rdcMessagePublisher, times(1)).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());

    // create verify mocks
    verify(lpnCacheService, times(1)).getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_NEW_DOCKTAG_LABEL_FORMAT_ENABLED,
            false);
    verify(containerService, times(0)).findByTrackingId(anyString());
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(anyInt());
    verify(instructionRepository, times(1)).saveAll(anyList());

    ArgumentCaptor<List<Container>> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(containerPersisterService, times(1)).saveContainers(listArgumentCaptor.capture());
    assertTrue(listArgumentCaptor.getValue().get(0).getContainerMiscInfo().size() > 0);
    assertTrue(
        listArgumentCaptor
            .getValue()
            .get(0)
            .getContainerMiscInfo()
            .containsKey(ReceivingConstants.DELIVERY_TYPE_CODE));
    assertTrue(
        listArgumentCaptor
            .getValue()
            .get(0)
            .getContainerMiscInfo()
            .containsKey(ReceivingConstants.FREIGHT_TYPE));

    verify(printJobService, times(1)).savePrintJobs(anyList());
    verify(rdcInstructionService, times(1))
        .publishContainerAndMove(anyString(), any(Container.class), any(HttpHeaders.class));
  }

  @Test
  private void
      testPartialDockTagCompleteApi_ThrowsException_WhenRetryFlagIsFalseAndFailureInLpnService()
          throws ReceivingException {
    // Complete mocks
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(getDockTag());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    doNothing().when(dockTagPersisterService).saveDockTag(any(DockTag.class));

    // Create mocks
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.PARTIAL_DOCKTAG_CREATION_ERROR,
                String.format(
                    ReceivingConstants.PARTIAL_DOCKTAG_CREATION_ERROR_MSG,
                    dockTagId,
                    getDockTag().getDeliveryNumber()),
                dockTagId,
                getDockTag().getDeliveryNumber()))
        .when(lpnCacheService)
        .getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class));

    CreateDockTagRequest createDockTagRequest = getCreateDockTagRequest();
    try {
      DockTagResponse response =
          rdcDockTagService.partialCompleteDockTag(
              createDockTagRequest, dockTagId, false, MockHttpHeaders.getHeaders());
    } catch (ReceivingInternalException excp) {
      assertNotNull(excp);
      assertTrue(excp.getErrorCode().equals(ExceptionCodes.PARTIAL_DOCKTAG_CREATION_ERROR));
    }

    // complete verify mocks
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryService, times(1)).deleteContainer(eq(dockTagId), any());
    verify(dockTagPersisterService, times(1)).saveDockTag(any(DockTag.class));

    // create verify mocks
    verify(lpnCacheService, times(1)).getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class));
  }

  @Test
  private void
      testPartialDockTagCompleteApi_ThrowsException_WhenRetryFlagIsFalseAndFailureInInventoryApi()
          throws ReceivingException {
    // Complete mocks
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(getDockTag());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.INVENTORY_NOT_FOUND,
                String.format(ReceivingConstants.INVENTORY_NOT_FOUND_MESSAGE, dockTagId)))
        .when(inventoryService)
        .deleteContainer(anyString(), any(HttpHeaders.class));

    CreateDockTagRequest createDockTagRequest = getCreateDockTagRequest();
    try {
      DockTagResponse response =
          rdcDockTagService.partialCompleteDockTag(
              createDockTagRequest, dockTagId, false, MockHttpHeaders.getHeaders());
    } catch (ReceivingInternalException excep) {
      assertNotNull(excep);
      assertTrue(excep.getErrorCode().equalsIgnoreCase(ExceptionCodes.INVENTORY_NOT_FOUND));
    }

    // complete verify mocks
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryService, times(1)).deleteContainer(eq(dockTagId), any());
  }

  @Test
  private void
      testPartialDockTagCompleteApi_ThrowsException_WhenRetryFlagIsFalseAndFailureInInventoryApiIsDown()
          throws ReceivingException {
    // Complete mocks
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(getDockTag());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.INVENTORY_SERVICE_NOT_AVAILABLE_ERROR,
                String.format(
                    ReceivingConstants.INVENTORY_SERVICE_NOT_AVAILABLE_ERROR_MSG, dockTagId),
                dockTagId))
        .when(inventoryService)
        .deleteContainer(anyString(), any(HttpHeaders.class));

    CreateDockTagRequest createDockTagRequest = getCreateDockTagRequest();
    try {
      DockTagResponse response =
          rdcDockTagService.partialCompleteDockTag(
              createDockTagRequest, dockTagId, false, MockHttpHeaders.getHeaders());
    } catch (ReceivingInternalException excep) {
      assertNotNull(excep);
      assertTrue(
          excep
              .getErrorCode()
              .equalsIgnoreCase(ExceptionCodes.INVENTORY_SERVICE_NOT_AVAILABLE_ERROR));
    }

    // complete verify mocks
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryService, times(1)).deleteContainer(eq(dockTagId), any());
  }

  @Test
  private void
      testPartialDockTagCompleteApi_ThrowsException_whenRetryFlagIsFalse_AndDockTagIsAlreadyReceived()
          throws ReceivingException {
    // Complete mocks
    DockTag dockTag = getDockTag();
    dockTag.setCompleteTs(new Date());
    dockTag.setCompleteUserId("sysadmin");
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(dockTag);

    CreateDockTagRequest createDockTagRequest = getCreateDockTagRequest();
    try {
      DockTagResponse response =
          rdcDockTagService.partialCompleteDockTag(
              createDockTagRequest, dockTagId, false, MockHttpHeaders.getHeaders());
    } catch (ReceivingInternalException excp) {
      assertNotNull(excp);
      assertTrue(
          excp.getErrorCode().equalsIgnoreCase(ExceptionCodes.DOCKTAG_ALREADY_COMPLETED_ERROR));
    }

    // complete verify mocks
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
  }

  @Test
  public void testCompleteDockTag_CoreImplementation() {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(getDockTag());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(0);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Central");
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryNumber(anyLong())).thenReturn(10);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList()))
        .thenReturn(3);
    doNothing().when(rdcMessagePublisher).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
    String dockTagResponse = rdcDockTagService.completeDockTag(dockTagId, headers);
    assertNotNull(dockTagResponse);

    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryService, times(1)).deleteContainer(eq(dockTagId), any());
    verify(dockTagPersisterService, times(1)).getCountOfDockTagsByDeliveryNumber(anyLong());
    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList());
    verify(rdcMessagePublisher, times(1)).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testReceiveUniversalTagException() {
    rdcDockTagService.receiveUniversalTag("123434", "TEST", MockHttpHeaders.getHeaders());
  }

  private List<DockTagData> getDockTagDBData() {
    return Arrays.asList(
        DockTagData.builder()
            .deliveryNumber(12345678L)
            .dockTagId("b328180000200000043976844")
            .build(),
        DockTagData.builder()
            .deliveryNumber(12345678L)
            .dockTagId("b328180000200000043976845")
            .build(),
        DockTagData.builder()
            .deliveryNumber(12345678L)
            .dockTagId("b328180000200000043976846")
            .build());
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

  private String getMockDelivery() {
    JsonObject mockDeliveryResponse = new JsonObject();
    mockDeliveryResponse.addProperty("deliveryNumber", 123L);
    mockDeliveryResponse.addProperty("deliveryStatus", DeliveryStatus.WRK.name());
    return mockDeliveryResponse.toString();
  }

  private Container getContainer() {
    Container container = new Container();
    container.setDeliveryNumber(12340001L);
    container.setTrackingId("b328180000200000043976844");
    container.setContainerException(ContainerException.DOCK_TAG.getText());
    container.setCreateUser("sysadmin");
    container.setInstructionId(99999L);
    container.setInventoryStatus(InventoryStatus.WORK_IN_PROGRESS.name());
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(ReceivingConstants.DELIVERY_TYPE_CODE, "DROP");
    containerMiscInfo.put(ReceivingConstants.FREIGHT_TYPE, "DA");
    container.setContainerMiscInfo(containerMiscInfo);
    return container;
  }

  private DockTag getCompletedDockTag() {
    DockTag dockTag = getDockTag();
    dockTag.setDockTagStatus(InstructionStatus.COMPLETED);
    dockTag.setCompleteTs(new Date());
    dockTag.setCompleteUserId("sysadmin");
    return dockTag;
  }

  private CreateDockTagRequest getCreateDockTagRequest() {
    CreateDockTagRequest createDockTagRequest =
        CreateDockTagRequest.builder()
            .deliveryNumber(12345678L)
            .doorNumber("100")
            .trailerNumber("344244")
            .carrier("CMC")
            .count(1)
            .build();
    return createDockTagRequest;
  }
}
