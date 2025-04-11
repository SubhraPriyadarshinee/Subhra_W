package com.walmart.move.nim.receiving.acc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_KOTLIN_CLIENT;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TRUE_STRING;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.mock.data.MockACLMessageData;
import com.walmart.move.nim.receiving.acc.mock.data.MockDockTag;
import com.walmart.move.nim.receiving.acc.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.acc.mock.data.MockReceipt;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.docktag.*;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DockTagType;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.joda.time.DateTime;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.junit.Assert;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ACCDockTagServiceTest extends ReceivingTestBase {

  @Mock private DockTagPersisterService dockTagPersisterService;
  @Mock private DeliveryService deliveryService;
  @Mock private ReceiptService receiptService;
  @Mock private InventoryService inventoryService;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private ACLDeliveryLinkService aclDeliveryLinkService;
  @Mock private ACCInstructionService accInstructionService;
  @Mock private LocationService locationService;
  @Mock private LabelDataService labelDataService;
  @Mock private ContainerService containerService;
  @Mock private InstructionService instructionService;
  @Mock private ContainerRepository containerRepository;
  @Mock private ObjectMapper objectMapper;
  @Mock private AppConfig appConfig;
  @InjectMocks private ACCDockTagService dockTagService;

  @Captor private ArgumentCaptor<List<Long>> deliveryCaptor;
  @Captor private ArgumentCaptor<List<InstructionStatus>> instructionStatusCaptor;
  @Captor private ArgumentCaptor<List<DockTag>> dockTagCaptor;

  private List<ReceiptSummaryResponse> receiptSummaryEachesResponse;

  private List<DeliveryDocument> deliveryDocuments;

  private ReceiveDockTagRequest receiveDockTagRequest;
  private ReceiveDockTagRequest multiManifestReceiveDockTagRequest;

  private Gson gson;
  private Gson gsonForDate;

  private String dockTagId = "c32987000000000000000001";
  private String dockTagId2 = "c32987000000000000000002";
  private String scannedLocation = "EPFLC08";
  private String scannedLocation2 = "EPFLC09";
  private String workstationLocation = "WS0001";

  private String deliveryDetailsJson;

  private LocationInfo locationResponseForPbylLocation;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32818);

    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
    gsonForDate =
        new GsonBuilder()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter("yyyy-MM-dd"))
            .create();

    receiptSummaryEachesResponse = new ArrayList<>();
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140004", 1, null, Long.valueOf(96)));
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140005", 1, null, Long.valueOf(96)));
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140007", 1, null, Long.valueOf(144)));

    locationResponseForPbylLocation =
        LocationInfo.builder()
            .isOnline(Boolean.FALSE)
            .mappedFloorLine("EFLCP14")
            .isFloorLine(Boolean.TRUE)
            .build();

    String dataPathDeliveryDocumentDA = null;
    try {
      dataPathDeliveryDocumentDA =
          new File(
                  "../../receiving-test/src/main/resources/json/DeliveryDocumentForItemScanDA.json")
              .getCanonicalPath();

      deliveryDocuments =
          Arrays.asList(
              new Gson()
                  .fromJson(
                      new String(Files.readAllBytes(Paths.get(dataPathDeliveryDocumentDA))),
                      DeliveryDocument[].class));
    } catch (IOException e) {
      fail("Error while reading JSON file");
    }

    receiveDockTagRequest = new ReceiveDockTagRequest();
    receiveDockTagRequest.setDockTagId(dockTagId);
    receiveDockTagRequest.setMappedFloorLineLocation(scannedLocation);

    multiManifestReceiveDockTagRequest = new ReceiveDockTagRequest();
    multiManifestReceiveDockTagRequest.setDockTagId(dockTagId2);
    multiManifestReceiveDockTagRequest.setMappedFloorLineLocation(scannedLocation2);
    multiManifestReceiveDockTagRequest.setWorkstationLocation(workstationLocation);

    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/GDMDeliveryDocument.json")
              .getCanonicalPath();
      deliveryDetailsJson = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @AfterMethod
  public void resetMocks() {
    reset(dockTagPersisterService);
    reset(deliveryService);
    reset(receiptService);
    reset(inventoryService);
    reset(locationService);
    reset(configUtils);
    reset(aclDeliveryLinkService);
    reset(labelDataService);
    reset(instructionService);
    reset(accInstructionService);
    reset(appConfig);
  }

  @BeforeMethod
  public void beforeMethod() {
    doReturn(aclDeliveryLinkService)
        .when(configUtils)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.DELIVERY_LINK_SERVICE), any());
    doReturn(deliveryService)
        .when(configUtils)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any());
  }

  @Test
  public void testCompleteDockTag_HappyFlow() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getDockTag());
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(0);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"WRK\"}");
    when(instructionService.hasOpenInstruction(anyLong())).thenReturn(Boolean.FALSE);
    DockTag dockTag =
        gson.fromJson(
            dockTagService.completeDockTag(dockTagId, MockHttpHeaders.getHeaders()), DockTag.class);
    assertEquals(dockTag.getDockTagStatus(), InstructionStatus.COMPLETED);
    assertEquals(dockTag.getCreateUserId(), "sysadmin");
    assertNotNull(dockTag.getCompleteTs());
    assertEquals(dockTag.getCompleteUserId(), "sysadmin");
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(dockTagPersisterService, times(0))
        .getCountOfDockTagsByDeliveryAndStatuses(
            eq(12340001L), eq(ReceivingUtils.getPendingDockTagStatus()));
    verify(inventoryService, times(1)).deleteContainer(eq(dockTagId), any());
    verify(deliveryService, times(1)).getDeliveryByDeliveryNumber(eq(12340001L), any());
    verify(receiptService, times(0)).getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
    verify(instructionService, times(0)).hasOpenInstruction(anyLong());
  }

  @Test
  public void testCompleteDockTag_CompletedDeliveryLastDockTag_HappyFlow()
      throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getDockTag());
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(0);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"COMPLETE\"}");
    doReturn(receiptSummaryEachesResponse)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    when(deliveryService.completeDelivery(anyLong(), anyBoolean(), any()))
        .thenReturn(new DeliveryInfo());
    when(instructionService.hasOpenInstruction(anyLong())).thenReturn(Boolean.FALSE);
    DockTag dockTag =
        gson.fromJson(
            dockTagService.completeDockTag(dockTagId, MockHttpHeaders.getHeaders()), DockTag.class);
    assertEquals(dockTag.getDockTagStatus(), InstructionStatus.COMPLETED);
    assertEquals(dockTag.getCreateUserId(), "sysadmin");
    assertNotNull(dockTag.getCompleteTs());
    assertEquals(dockTag.getCompleteUserId(), "sysadmin");
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(
            eq(12340001L), eq(ReceivingUtils.getPendingDockTagStatus()));
    verify(inventoryService, times(1)).deleteContainer(eq(dockTagId), any());
    verify(deliveryService, times(1)).getDeliveryByDeliveryNumber(eq(12340001L), any());
    verify(deliveryService, times(1)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test
  public void testCompleteDockTag_CompletedDeliveryLastDockTag_ExceptionDuringComplete()
      throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getDockTag());
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(0);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"COMPLETE\"}");
    doReturn(receiptSummaryEachesResponse)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    when(deliveryService.completeDelivery(anyLong(), anyBoolean(), any()))
        .thenThrow(new ReceivingException("Error", HttpStatus.INTERNAL_SERVER_ERROR));
    when(instructionService.hasOpenInstruction(anyLong())).thenReturn(Boolean.TRUE);
    DockTag dockTag =
        gson.fromJson(
            dockTagService.completeDockTag(dockTagId, MockHttpHeaders.getHeaders()), DockTag.class);
    assertEquals(dockTag.getDockTagStatus(), InstructionStatus.COMPLETED);
    assertEquals(dockTag.getCreateUserId(), "sysadmin");
    assertNotNull(dockTag.getCompleteTs());
    assertEquals(dockTag.getCompleteUserId(), "sysadmin");
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(
            eq(12340001L), eq(ReceivingUtils.getPendingDockTagStatus()));
    verify(inventoryService, times(1)).deleteContainer(eq(dockTagId), any());
    verify(deliveryService, times(1)).getDeliveryByDeliveryNumber(eq(12340001L), any());
    verify(deliveryService, times(1)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test
  public void testCompleteDockTag_CompletedDelivery_NotLastDockTag_HappyFlow()
      throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getDockTag());
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(1);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"COMPLETE\"}");
    doReturn(receiptSummaryEachesResponse)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    when(deliveryService.completeDelivery(anyLong(), anyBoolean(), any()))
        .thenReturn(new DeliveryInfo());
    when(instructionService.hasOpenInstruction(anyLong())).thenReturn(Boolean.FALSE);
    DockTag dockTag =
        gson.fromJson(
            dockTagService.completeDockTag(dockTagId, MockHttpHeaders.getHeaders()), DockTag.class);
    assertEquals(dockTag.getDockTagStatus(), InstructionStatus.COMPLETED);
    assertEquals(dockTag.getCreateUserId(), "sysadmin");
    assertNotNull(dockTag.getCompleteTs());
    assertEquals(dockTag.getCompleteUserId(), "sysadmin");
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(
            eq(12340001L), eq(ReceivingUtils.getPendingDockTagStatus()));
    verify(inventoryService, times(1)).deleteContainer(eq(dockTagId), any());
    verify(deliveryService, times(1)).getDeliveryByDeliveryNumber(eq(12340001L), any());
    verify(receiptService, times(0)).getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
    verify(instructionService, times(0)).hasOpenInstruction(anyLong());
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void testCompleteDockTag_DockTagNotFound_ExeptionScenario() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(null);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(1);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"COMPLETE\"}");
    doReturn(receiptSummaryEachesResponse)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    when(deliveryService.completeDelivery(anyLong(), anyBoolean(), any()))
        .thenReturn(new DeliveryInfo());
    when(instructionService.hasOpenInstruction(anyLong())).thenReturn(Boolean.FALSE);
    DockTag dockTag =
        gson.fromJson(
            dockTagService.completeDockTag(dockTagId, MockHttpHeaders.getHeaders()), DockTag.class);
    assertEquals(dockTag.getDockTagStatus(), InstructionStatus.COMPLETED);
    assertEquals(dockTag.getCreateUserId(), "sysadmin");
    assertNull(dockTag.getCompleteTs());
    assertEquals(dockTag.getCompleteUserId(), "sysadmin");
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(dockTagPersisterService, times(0))
        .getCountOfDockTagsByDeliveryAndStatuses(
            eq(12340001L), eq(ReceivingUtils.getPendingDockTagStatus()));
    verify(inventoryService, times(0)).deleteContainer(eq(dockTagId), any());
    verify(deliveryService, times(0)).getDeliveryByDeliveryNumber(eq(12340001L), any());
    verify(receiptService, times(0)).getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
    verify(instructionService, times(0)).hasOpenInstruction(anyLong());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testCompleteDockTag_DockTagCompleted_ExceptionScenario() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getCompletedDockTag());
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(1);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"COMPLETE\"}");
    doReturn(receiptSummaryEachesResponse)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    when(deliveryService.completeDelivery(anyLong(), anyBoolean(), any()))
        .thenReturn(new DeliveryInfo());
    when(instructionService.hasOpenInstruction(anyLong())).thenReturn(Boolean.FALSE);
    DockTag dockTag =
        gson.fromJson(
            dockTagService.completeDockTag(dockTagId, MockHttpHeaders.getHeaders()), DockTag.class);
    assertEquals(dockTag.getDockTagStatus(), InstructionStatus.COMPLETED);
    assertEquals(dockTag.getCreateUserId(), "sysadmin");
    assertNull(dockTag.getCompleteTs());
    assertEquals(dockTag.getCompleteUserId(), "sysadmin");
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(dockTagPersisterService, times(0))
        .getCountOfDockTagsByDeliveryAndStatuses(
            eq(12340001L), eq(ReceivingUtils.getPendingDockTagStatus()));
    verify(inventoryService, times(0)).deleteContainer(eq(dockTagId), any());
    verify(deliveryService, times(0)).getDeliveryByDeliveryNumber(eq(12340001L), any());
    verify(receiptService, times(0)).getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
    verify(instructionService, times(0)).hasOpenInstruction(anyLong());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testCompleteDockTag_ExceptionScenario() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getDockTag());
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(1);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    ErrorResponse gdmErrorResponse =
        ErrorResponse.builder()
            .errorMessage(ReceivingException.DELIVERY_NOT_FOUND)
            .errorCode(ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE)
            .errorKey(ExceptionCodes.DELIVERY_NOT_FOUND)
            .build();
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenThrow(new ReceivingException(HttpStatus.NOT_FOUND, gdmErrorResponse));
    doReturn(receiptSummaryEachesResponse)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    when(deliveryService.completeDelivery(anyLong(), anyBoolean(), any()))
        .thenReturn(new DeliveryInfo());
    when(instructionService.hasOpenInstruction(anyLong())).thenReturn(Boolean.FALSE);
    DockTag dockTag =
        gson.fromJson(
            dockTagService.completeDockTag(dockTagId, MockHttpHeaders.getHeaders()), DockTag.class);
    assertEquals(dockTag.getDockTagStatus(), InstructionStatus.COMPLETED);
    assertEquals(dockTag.getCreateUserId(), "sysadmin");
    assertNull(dockTag.getCompleteTs());
    assertEquals(dockTag.getCompleteUserId(), "sysadmin");
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(dockTagPersisterService, times(0))
        .getCountOfDockTagsByDeliveryAndStatuses(
            eq(12340001L), eq(ReceivingUtils.getPendingDockTagStatus()));
    verify(inventoryService, times(0)).deleteContainer(eq(dockTagId), any());
    verify(deliveryService, times(0)).getDeliveryByDeliveryNumber(eq(12340001L), any());
    verify(receiptService, times(0)).getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
    verify(instructionService, times(0)).hasOpenInstruction(anyLong());
  }

  @Test
  public void testPartialCompleteDockTag_HappyFlow() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getDockTag());
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(0);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"COMPLETE\"}");
    when(deliveryService.completeDelivery(anyLong(), anyBoolean(), any()))
        .thenReturn(new DeliveryInfo());
    when(instructionService.hasOpenInstruction(anyLong())).thenReturn(Boolean.FALSE);
    when(accInstructionService.createFloorLineDockTag(any(), any(HttpHeaders.class)))
        .thenReturn(
            new InstructionResponseImplNew(
                null, null, MockInstruction.getDockTagInstruction(), null));

    CreateDockTagRequest createDockTagRequest =
        CreateDockTagRequest.builder()
            .deliveryNumber(12340001L)
            .doorNumber("100")
            .dockTagType(DockTagType.FLOOR_LINE)
            .build();
    DockTagResponse dockTagResponse =
        dockTagService.partialCompleteDockTag(
            createDockTagRequest, dockTagId, false, MockHttpHeaders.getHeaders());

    assertEquals(dockTagResponse.getDockTags().size(), 1);
    assertNotNull(dockTagResponse.getDeliveryNumber());
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(1)).saveDockTag(dockTagArgumentCaptor.capture());
    DockTag dockTag = dockTagArgumentCaptor.getValue();
    assertNotNull(dockTag.getCompleteTs());
    assertNotNull(dockTag.getCompleteUserId());
    assertEquals(dockTag.getDockTagStatus(), InstructionStatus.COMPLETED);
    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(
            eq(12340001L), eq(ReceivingUtils.getPendingDockTagStatus()));
    verify(inventoryService, times(1)).deleteContainer(eq(dockTagId), any());
    verify(deliveryService, times(1)).getDeliveryByDeliveryNumber(eq(12340001L), any());
    verify(deliveryService, times(1)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test
  public void testPartialCompleteDockTag_ExistingDockTagAlreadyComplete()
      throws ReceivingException {
    DockTag alreadyCompleteDockTag = MockDockTag.getDockTag();
    alreadyCompleteDockTag.setCompleteTs(new Date());
    alreadyCompleteDockTag.setCompleteUserId("sysadmin");
    alreadyCompleteDockTag.setDockTagStatus(InstructionStatus.COMPLETED);
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(alreadyCompleteDockTag);
    InstructionResponse instructionResponse =
        new InstructionResponseImplNew(null, null, MockInstruction.getDockTagInstruction(), null);
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"COMPLETE\"}");
    when(deliveryService.completeDelivery(anyLong(), anyBoolean(), any()))
        .thenReturn(new DeliveryInfo());
    when(instructionService.hasOpenInstruction(anyLong())).thenReturn(Boolean.FALSE);
    when(accInstructionService.createFloorLineDockTag(any(), any(HttpHeaders.class)))
        .thenReturn(instructionResponse);
    try {
      CreateDockTagRequest createDockTagRequest =
          CreateDockTagRequest.builder()
              .deliveryNumber(12340001L)
              .doorNumber("100")
              .dockTagType(DockTagType.FLOOR_LINE)
              .build();
      DockTagResponse dockTagResponse =
          dockTagService.partialCompleteDockTag(
              createDockTagRequest, dockTagId, false, MockHttpHeaders.getHeaders());
    } catch (ReceivingInternalException exc) {
      assertEquals(exc.getErrorCode(), ExceptionCodes.DOCKTAG_ALREADY_COMPLETED_ERROR);
      verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
      verify(dockTagPersisterService, never()).saveDockTag(any());
      verify(dockTagPersisterService, never())
          .getCountOfDockTagsByDeliveryAndStatuses(
              eq(12340001L), eq(ReceivingUtils.getPendingDockTagStatus()));
      verify(inventoryService, never()).deleteContainer(eq(dockTagId), any());
      verify(deliveryService, never()).getDeliveryByDeliveryNumber(eq(12340001L), any());
      verify(deliveryService, never()).completeDelivery(anyLong(), anyBoolean(), any());
    }
  }

  @Test
  public void testPartialCompleteDockTag_ExistingDockTagAlreadyComplete_RetryFlow()
      throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getCompletedDockTag());
    InstructionResponse instructionResponse =
        new InstructionResponseImplNew(null, null, MockInstruction.getDockTagInstruction(), null);
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"COMPLETE\"}");
    when(deliveryService.completeDelivery(anyLong(), anyBoolean(), any()))
        .thenReturn(new DeliveryInfo());
    when(instructionService.hasOpenInstruction(anyLong())).thenReturn(Boolean.FALSE);
    when(accInstructionService.createFloorLineDockTag(any(), any(HttpHeaders.class)))
        .thenReturn(instructionResponse);
    CreateDockTagRequest createDockTagRequest =
        CreateDockTagRequest.builder()
            .deliveryNumber(12340001L)
            .doorNumber("100")
            .dockTagType(DockTagType.FLOOR_LINE)
            .build();
    DockTagResponse dockTagResponse =
        dockTagService.partialCompleteDockTag(
            createDockTagRequest, dockTagId, true, MockHttpHeaders.getHeaders());

    assertEquals(dockTagResponse.getDockTags().size(), 1);
    assertNotNull(dockTagResponse.getDeliveryNumber());
    verify(accInstructionService, times(1))
        .createFloorLineDockTag(any(InstructionRequest.class), any());
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(dockTagPersisterService, never()).saveDockTag(any());
    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(
            eq(12340001L), eq(ReceivingUtils.getPendingDockTagStatus()));
    verify(inventoryService, never()).deleteContainer(eq(dockTagId), any());
    verify(deliveryService, times(1)).getDeliveryByDeliveryNumber(eq(12340001L), any());
    verify(deliveryService, times(1)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test
  public void testPartialCompleteDockTag_DockTagNotFound() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(null);
    try {
      CreateDockTagRequest createDockTagRequest =
          CreateDockTagRequest.builder()
              .deliveryNumber(12340001L)
              .doorNumber("100")
              .dockTagType(DockTagType.FLOOR_LINE)
              .build();
      DockTagResponse dockTagResponse =
          dockTagService.partialCompleteDockTag(
              createDockTagRequest, dockTagId, false, MockHttpHeaders.getHeaders());
    } catch (ReceivingInternalException exc) {
      assertEquals(exc.getErrorCode(), ExceptionCodes.DOCK_TAG_NOT_FOUND);
      verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
      verify(accInstructionService, never())
          .createFloorLineDockTag(any(InstructionRequest.class), any());
      verify(dockTagPersisterService, never()).saveDockTag(any());
      verify(dockTagPersisterService, never())
          .getCountOfDockTagsByDeliveryAndStatuses(
              eq(12340001L), eq(ReceivingUtils.getPendingDockTagStatus()));
      verify(inventoryService, never()).deleteContainer(eq(dockTagId), any());
      verify(deliveryService, never()).getDeliveryByDeliveryNumber(eq(12340001L), any());
      verify(deliveryService, never()).completeDelivery(anyLong(), anyBoolean(), any());
    }
  }

  @Test
  public void testPartialCompleteDockTag_ExceptionInInventory() throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.set(IS_KOTLIN_CLIENT, TRUE_STRING);
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getDockTag());
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.UNABLE_TO_PROCESS_INVENTORY,
                ReceivingConstants.INVENTORY_SERVICE_DOWN))
        .when(inventoryService)
        .deleteContainer(eq(dockTagId), any());
    InstructionResponse instructionResponse =
        new InstructionResponseImplNew(null, null, MockInstruction.getDockTagInstruction(), null);
    when(accInstructionService.createFloorLineDockTag(any(), any(HttpHeaders.class)))
        .thenReturn(instructionResponse);
    try {
      CreateDockTagRequest createDockTagRequest =
          CreateDockTagRequest.builder()
              .deliveryNumber(12340001L)
              .doorNumber("100")
              .dockTagType(DockTagType.FLOOR_LINE)
              .build();
      DockTagResponse dockTagResponse =
          dockTagService.partialCompleteDockTag(createDockTagRequest, dockTagId, false, headers);

    } catch (ReceivingInternalException exc) {
      assertEquals(exc.getErrorCode(), ExceptionCodes.INVENTORY_SERVICE_NOT_AVAILABLE_ERROR);
      verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
      verify(dockTagPersisterService, never()).saveDockTag(any());
      verify(inventoryService, times(1)).deleteContainer(eq(dockTagId), any());
      verify(deliveryService, never()).getDeliveryByDeliveryNumber(eq(12340001L), any());
      verify(accInstructionService, never())
          .createFloorLineDockTag(any(InstructionRequest.class), any());
      verify(dockTagPersisterService, never())
          .getCountOfDockTagsByDeliveryAndStatuses(
              eq(12340001L), eq(ReceivingUtils.getPendingDockTagStatus()));
      verify(deliveryService, never()).completeDelivery(anyLong(), anyBoolean(), any());
    }
  }

  @Test
  public void testPartialCompleteDockTag_ExceptionInCreateDockTag_ExistingDockTagComplete()
      throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getCompletedDockTag());
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(0);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"COMPLETE\"}");
    when(deliveryService.completeDelivery(anyLong(), anyBoolean(), any()))
        .thenReturn(new DeliveryInfo());
    when(instructionService.hasOpenInstruction(anyLong())).thenReturn(Boolean.FALSE);
    when(accInstructionService.createFloorLineDockTag(any(), any(HttpHeaders.class)))
        .thenThrow(new ReceivingException("Create Instruction Error Message"));
    try {
      CreateDockTagRequest createDockTagRequest =
          CreateDockTagRequest.builder()
              .deliveryNumber(12340001L)
              .doorNumber("100")
              .dockTagType(DockTagType.FLOOR_LINE)
              .build();
      DockTagResponse dockTagResponse =
          dockTagService.partialCompleteDockTag(
              createDockTagRequest, dockTagId, true, MockHttpHeaders.getHeaders());
    } catch (ReceivingInternalException exc) {
      assertEquals(exc.getErrorCode(), ExceptionCodes.PARTIAL_DOCKTAG_CREATION_ERROR);
      verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
      verify(inventoryService, never()).deleteContainer(eq(dockTagId), any());
      verify(deliveryService, never()).getDeliveryByDeliveryNumber(eq(12340001L), any());
      verify(dockTagPersisterService, never())
          .getCountOfDockTagsByDeliveryAndStatuses(
              eq(12340001L), eq(ReceivingUtils.getPendingDockTagStatus()));
      verify(deliveryService, never()).completeDelivery(anyLong(), anyBoolean(), any());
    }
  }

  @Test
  public void testReceiveDockTag_HappyFlow() {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getDockTag());
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    InstructionResponse instructionResponse =
        dockTagService.receiveDockTag(receiveDockTagRequest, headers);
    assertNotNull(instructionResponse);
    Assert.assertEquals(
        ReceivingConstants.RECEIVING_PROVIDER_ID,
        instructionResponse.getInstruction().getProviderId());
    Assert.assertEquals(
        "Place On Conveyor", instructionResponse.getInstruction().getInstructionCode());
    Assert.assertEquals(
        "Place item on conveyor instruction",
        instructionResponse.getInstruction().getInstructionMsg());
    Assert.assertEquals(
        new Long(12340001L), instructionResponse.getInstruction().getDeliveryNumber());
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(1)).saveDockTag(dockTagArgumentCaptor.capture());
    DockTag dockTag = dockTagArgumentCaptor.getValue();
    assertEquals(dockTag.getDockTagStatus(), InstructionStatus.UPDATED);
    assertEquals(dockTag.getScannedLocation(), receiveDockTagRequest.getMappedFloorLineLocation());
    assertEquals(
        dockTag.getLastChangedUserId(), headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    verify(inventoryService, times(0)).deleteContainer(eq(dockTagId), any());
    verify(aclDeliveryLinkService, times(1)).updateDeliveryLink(any(), any());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveDockTag_DockTagCompleted_ExceptionScenario() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getCompletedDockTag());
    InstructionResponse instructionResponse =
        dockTagService.receiveDockTag(receiveDockTagRequest, MockHttpHeaders.getHeaders());
    verify(dockTagPersisterService, times(0)).getDockTagByDockTagId(eq(dockTagId));
    verify(inventoryService, times(0)).deleteContainer(eq(dockTagId), any());
    verify(aclDeliveryLinkService, times(0)).updateDeliveryLink(any(), any());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveDockTag_NonConDockTag_ExceptionScenario() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getNonConDockTag());
    InstructionResponse instructionResponse =
        dockTagService.receiveDockTag(receiveDockTagRequest, MockHttpHeaders.getHeaders());
    verify(dockTagPersisterService, times(0)).getDockTagByDockTagId(eq(dockTagId));
    verify(inventoryService, times(0)).deleteContainer(eq(dockTagId), any());
    verify(aclDeliveryLinkService, times(0)).updateDeliveryLink(any(), any());
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void testReceiveDockTag_DockTagNotFound_ExceptionScenario() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(null);
    InstructionResponse instructionResponse =
        dockTagService.receiveDockTag(receiveDockTagRequest, MockHttpHeaders.getHeaders());
    assertNotNull(instructionResponse);
    Assert.assertEquals(
        ReceivingConstants.RECEIVING_PROVIDER_ID,
        instructionResponse.getInstruction().getProviderId());
    Assert.assertEquals(
        "Place On Conveyor", instructionResponse.getInstruction().getInstructionCode());
    Assert.assertEquals(
        "Place item on conveyor instruction",
        instructionResponse.getInstruction().getInstructionMsg());
    verify(dockTagPersisterService, times(0)).getDockTagByDockTagId(eq(dockTagId));
    verify(inventoryService, times(0)).deleteContainer(eq(dockTagId), any());
    verify(aclDeliveryLinkService, times(0)).updateDeliveryLink(any(), any());
  }

  @Test
  public void testReceiveDockTag_MultiManifest_HappyFlow() throws ReceivingException {
    Receipt receiptWithinIdleTime = MockReceipt.getReceipt();
    receiptWithinIdleTime.setCreateTs((new DateTime().minusHours(5)).toDate());

    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId2)))
        .thenReturn(MockDockTag.getDockTag2());
    when(dockTagPersisterService.getAttachedDockTagsByScannedLocation(eq(scannedLocation2)))
        .thenReturn(MockDockTag.getMultiManifestDockTags());
    when(receiptService.findLatestReceiptByDeliveryNumber(anyLong()))
        .thenReturn(receiptWithinIdleTime);
    when(labelDataService.findIfCommonItemExistsForDeliveries(anyList(), anyLong()))
        .thenReturn(Collections.emptyList());
    when(configUtils.getCcmConfigValue(anyInt(), anyString())).thenReturn(gson.toJsonTree(10));
    when(configUtils.isFeatureFlagEnabled(anyString())).thenReturn(Boolean.TRUE);
    when(deliveryService.getDeliveryByDeliveryNumber(eq(12340001L), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"WRK\"}");
    when(deliveryService.getDeliveryByDeliveryNumber(eq(12340002L), any()))
        .thenReturn("{\"deliveryNumber\":12340002,\"deliveryStatus\":\"WRK\"}");

    InstructionResponse instructionResponse =
        dockTagService.receiveDockTag(
            multiManifestReceiveDockTagRequest, MockHttpHeaders.getHeaders());
    assertNotNull(instructionResponse);
    Assert.assertEquals(
        ReceivingConstants.RECEIVING_PROVIDER_ID,
        instructionResponse.getInstruction().getProviderId());
    Assert.assertEquals(
        "Place On Conveyor", instructionResponse.getInstruction().getInstructionCode());
    Assert.assertEquals(
        "Place item on conveyor instruction",
        instructionResponse.getInstruction().getInstructionMsg());
    verify(dockTagPersisterService, times(0)).getDockTagByDockTagId(eq(dockTagId));
    verify(dockTagPersisterService, times(1))
        .getAttachedDockTagsByScannedLocation(eq(scannedLocation2));
    verify(dockTagPersisterService, times(0)).saveAllDockTags(any());
    verify(receiptService, times(2)).findLatestReceiptByDeliveryNumber(anyLong());
    verify(labelDataService, times(1)).findIfCommonItemExistsForDeliveries(anyList(), anyLong());
    verify(inventoryService, times(0)).deleteContainer(eq(dockTagId), any());
    verify(aclDeliveryLinkService, times(1)).updateDeliveryLink(any(), any());
    verify(deliveryService, times(0)).getDeliveryByDeliveryNumber(eq(12340001L), any());
    verify(deliveryService, times(0)).getDeliveryByDeliveryNumber(eq(12340002L), any());
  }

  @Test
  public void testReceiveDockTag_MultiManifest_PartialStaleDockTags() throws ReceivingException {
    Receipt receiptWithinIdleTime = MockReceipt.getReceipt();
    receiptWithinIdleTime.setCreateTs((new DateTime().minusHours(5)).toDate());
    Receipt receiptOutsideIdleTime = MockReceipt.getReceipt();
    receiptOutsideIdleTime.setCreateTs((new DateTime().minusHours(15)).toDate());
    List<DockTag> multiManifestDockTags = MockDockTag.getMultiManifestDockTags();
    multiManifestDockTags
        .get(0)
        .setLastChangedTs(new Date(new Date().getTime() - 11 * 60 * 60 * 1000));
    multiManifestDockTags
        .get(1)
        .setLastChangedTs(new Date(new Date().getTime() - 9 * 60 * 60 * 1000));

    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId2)))
        .thenReturn(MockDockTag.getDockTag2());
    when(dockTagPersisterService.getAttachedDockTagsByScannedLocation(eq(scannedLocation2)))
        .thenReturn(multiManifestDockTags);
    when(receiptService.findLatestReceiptByDeliveryNumber(eq(12340001L)))
        .thenReturn(receiptOutsideIdleTime);
    when(receiptService.findLatestReceiptByDeliveryNumber(eq(12340002L)))
        .thenReturn(receiptOutsideIdleTime);
    when(labelDataService.findIfCommonItemExistsForDeliveries(anyList(), anyLong()))
        .thenReturn(Collections.emptyList());
    when(configUtils.getCcmConfigValue(anyInt(), anyString())).thenReturn(gson.toJsonTree(10));
    when(configUtils.isFeatureFlagEnabled(anyString())).thenReturn(Boolean.TRUE);
    when(deliveryService.getDeliveryByDeliveryNumber(eq(12340001L), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"WRK\"}");
    when(deliveryService.getDeliveryByDeliveryNumber(eq(12340002L), any()))
        .thenReturn("{\"deliveryNumber\":12340002,\"deliveryStatus\":\"WRK\"}");

    InstructionResponse instructionResponse =
        dockTagService.receiveDockTag(
            multiManifestReceiveDockTagRequest, MockHttpHeaders.getHeaders());
    assertNotNull(instructionResponse);
    Assert.assertEquals(
        ReceivingConstants.RECEIVING_PROVIDER_ID,
        instructionResponse.getInstruction().getProviderId());
    Assert.assertEquals(
        "Place On Conveyor", instructionResponse.getInstruction().getInstructionCode());
    Assert.assertEquals(
        "Place item on conveyor instruction",
        instructionResponse.getInstruction().getInstructionMsg());
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId2));
    verify(dockTagPersisterService, times(1))
        .getAttachedDockTagsByScannedLocation(eq(scannedLocation2));
    verify(dockTagPersisterService, times(1)).saveAllDockTags(any());
    verify(receiptService, times(2)).findLatestReceiptByDeliveryNumber(anyLong());
    verify(labelDataService, times(0)).findIfCommonItemExistsForDeliveries(anyList(), anyLong());
    verify(inventoryService, times(0)).deleteContainer(eq(dockTagId2), any());
    verify(aclDeliveryLinkService, times(1)).updateDeliveryLink(any(), any());
    verify(deliveryService, times(1)).getDeliveryByDeliveryNumber(anyLong(), any());
  }

  @Test
  public void testReceiveDockTag_MultiManifest_AllStaleDockTags() throws ReceivingException {
    Receipt receiptOutsideIdleTime = MockReceipt.getReceipt();
    receiptOutsideIdleTime.setCreateTs((new DateTime().minusHours(15)).toDate());
    List<DockTag> multiManifestDockTags = MockDockTag.getMultiManifestDockTags();
    multiManifestDockTags
        .get(0)
        .setLastChangedTs(new Date(new Date().getTime() - 11 * 60 * 60 * 1000));
    multiManifestDockTags
        .get(1)
        .setLastChangedTs(new Date(new Date().getTime() - 11 * 60 * 60 * 1000));
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId2)))
        .thenReturn(MockDockTag.getDockTag2());
    when(dockTagPersisterService.getAttachedDockTagsByScannedLocation(eq(scannedLocation2)))
        .thenReturn(multiManifestDockTags);
    when(receiptService.findLatestReceiptByDeliveryNumber(anyLong()))
        .thenReturn(receiptOutsideIdleTime);
    when(labelDataService.findIfCommonItemExistsForDeliveries(anyList(), anyLong()))
        .thenReturn(Collections.emptyList());
    when(configUtils.getCcmConfigValue(anyInt(), anyString())).thenReturn(gson.toJsonTree(10));
    when(configUtils.isFeatureFlagEnabled(anyString())).thenReturn(Boolean.TRUE);
    when(deliveryService.getDeliveryByDeliveryNumber(eq(12340001L), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"WRK\"}");
    when(deliveryService.getDeliveryByDeliveryNumber(eq(12340002L), any()))
        .thenReturn("{\"deliveryNumber\":12340002,\"deliveryStatus\":\"WRK\"}");

    InstructionResponse instructionResponse =
        dockTagService.receiveDockTag(
            multiManifestReceiveDockTagRequest, MockHttpHeaders.getHeaders());
    assertNotNull(instructionResponse);
    Assert.assertEquals(
        ReceivingConstants.RECEIVING_PROVIDER_ID,
        instructionResponse.getInstruction().getProviderId());
    Assert.assertEquals(
        "Place On Conveyor", instructionResponse.getInstruction().getInstructionCode());
    Assert.assertEquals(
        "Place item on conveyor instruction",
        instructionResponse.getInstruction().getInstructionMsg());
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId2));
    verify(dockTagPersisterService, times(1))
        .getAttachedDockTagsByScannedLocation(eq(scannedLocation2));
    verify(dockTagPersisterService, times(1)).saveAllDockTags(any());
    verify(receiptService, times(2)).findLatestReceiptByDeliveryNumber(anyLong());
    verify(labelDataService, times(0)).findIfCommonItemExistsForDeliveries(anyList(), anyLong());
    verify(inventoryService, times(0)).deleteContainer(eq(dockTagId2), any());
    verify(aclDeliveryLinkService, times(1)).updateDeliveryLink(any(), any());
    verify(deliveryService, times(1)).getDeliveryByDeliveryNumber(anyLong(), any());
  }

  @Test
  public void testReceiveDockTag_MultiManifest_DeliveryStaleDockTagsNotStale()
      throws ReceivingException {
    Receipt receiptOutsideIdleTime = MockReceipt.getReceipt();
    receiptOutsideIdleTime.setCreateTs((new DateTime().minusHours(15)).toDate());
    List<DockTag> multiManifestDockTags = MockDockTag.getMultiManifestDockTags();
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId2)))
        .thenReturn(MockDockTag.getDockTag2());
    when(dockTagPersisterService.getAttachedDockTagsByScannedLocation(eq(scannedLocation2)))
        .thenReturn(multiManifestDockTags);
    when(receiptService.findLatestReceiptByDeliveryNumber(anyLong()))
        .thenReturn(receiptOutsideIdleTime);
    when(labelDataService.findIfCommonItemExistsForDeliveries(anyList(), anyLong()))
        .thenReturn(Collections.emptyList());
    when(configUtils.getCcmConfigValue(anyInt(), anyString())).thenReturn(gson.toJsonTree(10));
    when(configUtils.isFeatureFlagEnabled(anyString())).thenReturn(Boolean.TRUE);
    when(deliveryService.getDeliveryByDeliveryNumber(eq(12340001L), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"WRK\"}");
    when(deliveryService.getDeliveryByDeliveryNumber(eq(12340002L), any()))
        .thenReturn("{\"deliveryNumber\":12340002,\"deliveryStatus\":\"WRK\"}");

    InstructionResponse instructionResponse =
        dockTagService.receiveDockTag(
            multiManifestReceiveDockTagRequest, MockHttpHeaders.getHeaders());
    assertNotNull(instructionResponse);
    Assert.assertEquals(
        ReceivingConstants.RECEIVING_PROVIDER_ID,
        instructionResponse.getInstruction().getProviderId());
    Assert.assertEquals(
        "Place On Conveyor", instructionResponse.getInstruction().getInstructionCode());
    Assert.assertEquals(
        "Place item on conveyor instruction",
        instructionResponse.getInstruction().getInstructionMsg());
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId2));
    verify(dockTagPersisterService, times(1))
        .getAttachedDockTagsByScannedLocation(eq(scannedLocation2));
    verify(dockTagPersisterService, times(0)).saveAllDockTags(any());
    verify(receiptService, times(2)).findLatestReceiptByDeliveryNumber(anyLong());
    verify(labelDataService, times(1)).findIfCommonItemExistsForDeliveries(anyList(), anyLong());
    verify(inventoryService, times(0)).deleteContainer(eq(dockTagId), any());
    verify(aclDeliveryLinkService, times(1)).updateDeliveryLink(any(), any());
    verify(deliveryService, times(0)).getDeliveryByDeliveryNumber(eq(12340001L), any());
    verify(deliveryService, times(0)).getDeliveryByDeliveryNumber(eq(12340002L), any());
  }

  @Test
  public void testReceiveDockTag_MultiManifest_NoStaleDockTags_SingleActiveDelivery() {
    Receipt receiptWithinIdleTime = MockReceipt.getReceipt();
    receiptWithinIdleTime.setCreateTs((new DateTime().minusHours(5)).toDate());
    DockTag dockTag = MockDockTag.getDockTag2();
    dockTag.setDeliveryNumber(12340001L);
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId2))).thenReturn(dockTag);
    when(dockTagPersisterService.getAttachedDockTagsByScannedLocation(eq(scannedLocation2)))
        .thenReturn(MockDockTag.getMultiManifestDockTags_SameDelivery());
    when(receiptService.findLatestReceiptByDeliveryNumber(anyLong()))
        .thenReturn(receiptWithinIdleTime);
    when(labelDataService.findIfCommonItemExistsForDeliveries(anyList(), anyLong()))
        .thenReturn(Collections.emptyList());
    when(configUtils.getCcmConfigValue(anyInt(), anyString())).thenReturn(gson.toJsonTree(10));
    when(configUtils.isFeatureFlagEnabled(anyString())).thenReturn(Boolean.TRUE);
    InstructionResponse instructionResponse =
        dockTagService.receiveDockTag(
            multiManifestReceiveDockTagRequest, MockHttpHeaders.getHeaders());
    assertNotNull(instructionResponse);
    Assert.assertEquals(
        ReceivingConstants.RECEIVING_PROVIDER_ID,
        instructionResponse.getInstruction().getProviderId());
    Assert.assertEquals(
        "Place On Conveyor", instructionResponse.getInstruction().getInstructionCode());
    Assert.assertEquals(
        "Place item on conveyor instruction",
        instructionResponse.getInstruction().getInstructionMsg());
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId2));
    verify(dockTagPersisterService, times(1))
        .getAttachedDockTagsByScannedLocation(eq(scannedLocation2));
    verify(dockTagPersisterService, times(0)).saveAllDockTags(any());
    verify(receiptService, times(1)).findLatestReceiptByDeliveryNumber(anyLong());
    verify(labelDataService, times(0)).findIfCommonItemExistsForDeliveries(anyList(), anyLong());
    verify(inventoryService, times(0)).deleteContainer(eq(dockTagId2), any());
    verify(aclDeliveryLinkService, times(1)).updateDeliveryLink(any(), any());
  }

  @Test
  public void testReceiveDockTag_MultiManifest_CommonItemException() throws ReceivingException {
    Receipt receiptWithinIdleTime = MockReceipt.getReceipt();
    receiptWithinIdleTime.setCreateTs((new DateTime().minusHours(5)).toDate());
    when(configUtils.isFeatureFlagEnabled(
            eq(ReceivingConstants.FLOORLINE_ITEM_COLLISION_SUCCESS_RESPONSE_ENABLED)))
        .thenReturn(false);
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId2)))
        .thenReturn(MockDockTag.getDockTag2());
    when(dockTagPersisterService.getAttachedDockTagsByScannedLocation(eq(scannedLocation2)))
        .thenReturn(MockDockTag.getMultiManifestDockTags());
    when(receiptService.findLatestReceiptByDeliveryNumber(anyLong()))
        .thenReturn(receiptWithinIdleTime);
    when(labelDataService.findIfCommonItemExistsForDeliveries(anyList(), anyLong()))
        .thenReturn(Collections.singletonList(1234567890L));
    when(configUtils.getCcmConfigValue(anyInt(), anyString())).thenReturn(gson.toJsonTree(10));
    when(configUtils.isFeatureFlagEnabled(anyString())).thenReturn(Boolean.TRUE);
    when(deliveryService.getDeliveryByDeliveryNumber(eq(12340001L), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"WRK\"}");
    when(deliveryService.getDeliveryByDeliveryNumber(eq(12340002L), any()))
        .thenReturn("{\"deliveryNumber\":12340002,\"deliveryStatus\":\"WRK\"}");
    try {
      InstructionResponse instructionResponse =
          dockTagService.receiveDockTag(
              multiManifestReceiveDockTagRequest, MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException exc) {
      assertEquals(ExceptionCodes.CONFLICT_DOCK_TAG_MMR, exc.getErrorCode());
    }
  }

  @Test
  public void
      testReceiveDockTag_MultiManifest_CommonItemException_ItemCollisionSuccessResponseEnabled()
          throws ReceivingException {
    Receipt receiptWithinIdleTime = MockReceipt.getReceipt();
    receiptWithinIdleTime.setCreateTs((new DateTime().minusHours(5)).toDate());
    when(configUtils.isFeatureFlagEnabled(
            eq(ReceivingConstants.FLOORLINE_ITEM_COLLISION_SUCCESS_RESPONSE_ENABLED)))
        .thenReturn(true);
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId2)))
        .thenReturn(MockDockTag.getDockTag2());
    when(dockTagPersisterService.getAttachedDockTagsByScannedLocation(eq(scannedLocation2)))
        .thenReturn(MockDockTag.getMultiManifestDockTags());
    when(receiptService.findLatestReceiptByDeliveryNumber(anyLong()))
        .thenReturn(receiptWithinIdleTime);
    when(labelDataService.findIfCommonItemExistsForDeliveries(anyList(), anyLong()))
        .thenReturn(Collections.singletonList(1234567890L));
    when(configUtils.getCcmConfigValue(anyInt(), anyString())).thenReturn(gson.toJsonTree(10));
    when(configUtils.isFeatureFlagEnabled(anyString())).thenReturn(Boolean.TRUE);
    when(deliveryService.getDeliveryByDeliveryNumber(eq(12340001L), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"WRK\"}");
    when(deliveryService.getDeliveryByDeliveryNumber(eq(12340002L), any()))
        .thenReturn("{\"deliveryNumber\":12340002,\"deliveryStatus\":\"WRK\"}");
    InstructionResponse instructionResponse =
        dockTagService.receiveDockTag(
            multiManifestReceiveDockTagRequest, MockHttpHeaders.getHeaders());

    assertEquals(
        ReceivingConstants.ITEM_COLLISION_INSTRUCTION_CODE,
        instructionResponse.getInstruction().getInstructionCode());
  }

  @Test(
      expectedExceptions = ReceivingConflictException.class,
      expectedExceptionsMessageRegExp = "Scanned location WS0001 already has a dock tag attached")
  public void testReceiveDockTag_MultiManifest_SameLocationException() throws ReceivingException {
    Receipt receiptWithinIdleTime = MockReceipt.getReceipt();
    receiptWithinIdleTime.setCreateTs((new DateTime().minusHours(5)).toDate());

    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId2)))
        .thenReturn(MockDockTag.getDockTag2());
    when(dockTagPersisterService.getAttachedDockTagsByScannedLocation(eq(scannedLocation2)))
        .thenReturn(MockDockTag.getMultiManifestDockTags_SameLocationAsScanned());
    when(receiptService.findLatestReceiptByDeliveryNumber(anyLong()))
        .thenReturn(receiptWithinIdleTime);
    when(labelDataService.findIfCommonItemExistsForDeliveries(anyList(), anyLong()))
        .thenReturn(Collections.emptyList());
    when(configUtils.getCcmConfigValue(anyInt(), anyString())).thenReturn(gson.toJsonTree(10));
    when(configUtils.isFeatureFlagEnabled(anyString())).thenReturn(Boolean.TRUE);
    when(configUtils.isFeatureFlagEnabled(
            eq(ReceivingConstants.FLOORLINE_ITEM_COLLISION_SUCCESS_RESPONSE_ENABLED)))
        .thenReturn(Boolean.FALSE);
    when(deliveryService.getDeliveryByDeliveryNumber(eq(12340001L), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"WRK\"}");
    when(deliveryService.getDeliveryByDeliveryNumber(eq(12340002L), any()))
        .thenReturn("{\"deliveryNumber\":12340002,\"deliveryStatus\":\"WRK\"}");

    InstructionResponse instructionResponse =
        dockTagService.receiveDockTag(
            multiManifestReceiveDockTagRequest, MockHttpHeaders.getHeaders());
    assertNotNull(instructionResponse);
    Assert.assertEquals(
        ReceivingConstants.RECEIVING_PROVIDER_ID,
        instructionResponse.getInstruction().getProviderId());
    Assert.assertEquals(
        "Place On Conveyor", instructionResponse.getInstruction().getInstructionCode());
    Assert.assertEquals(
        "Place item on conveyor instruction",
        instructionResponse.getInstruction().getInstructionMsg());
    verify(dockTagPersisterService, times(0)).getDockTagByDockTagId(eq(dockTagId));
    verify(dockTagPersisterService, times(1))
        .getAttachedDockTagsByScannedLocation(eq(scannedLocation2));
    verify(dockTagPersisterService, times(0)).saveAllDockTags(any());
    verify(receiptService, times(2)).findLatestReceiptByDeliveryNumber(anyLong());
    verify(labelDataService, times(1)).findIfCommonItemExistsForDeliveries(anyList(), anyLong());
    verify(inventoryService, times(0)).deleteContainer(eq(dockTagId), any());
    verify(aclDeliveryLinkService, times(1)).updateDeliveryLink(any(), any());
    verify(deliveryService, times(0)).getDeliveryByDeliveryNumber(eq(12340001L), any());
    verify(deliveryService, times(0)).getDeliveryByDeliveryNumber(eq(12340002L), any());
  }

  @Test
  public void testUpdateDockTagById() {
    DockTag dockTag = MockDockTag.getDockTag();
    when(dockTagPersisterService.getDockTagByDockTagId(dockTag.getDockTagId())).thenReturn(dockTag);
    String user = "user";
    dockTagService.updateDockTagById(dockTag.getDockTagId(), InstructionStatus.COMPLETED, user);
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(dockTag.getDockTagId());
    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(1)).saveDockTag(dockTagArgumentCaptor.capture());
    DockTag savedDockTag = dockTagArgumentCaptor.getValue();
    assertEquals(savedDockTag.getDockTagStatus(), InstructionStatus.COMPLETED);
    assertEquals(savedDockTag.getCompleteUserId(), user);
    assertNotNull(savedDockTag.getCompleteTs());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testSearchDockTagInvalidDel() {
    List<String> deliveryNumbers = Arrays.asList("a12345");
    SearchDockTagRequest searchDockTagRequest =
        SearchDockTagRequest.builder().deliveryNumbers(deliveryNumbers).build();
    dockTagService.searchDockTag(searchDockTagRequest, null);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testSearchDockTagEmptyDel() {
    List<String> deliveryNumbers = Arrays.asList("");
    SearchDockTagRequest searchDockTagRequest =
        SearchDockTagRequest.builder().deliveryNumbers(deliveryNumbers).build();
    dockTagService.searchDockTag(searchDockTagRequest, null);
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
        dockTagService.searchDockTag(searchDockTagRequest, null), gson.toJson(dockTagList));

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
        dockTagService.searchDockTag(searchDockTagRequest, InstructionStatus.CREATED),
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
        dockTagService.searchDockTag(searchDockTagRequest, InstructionStatus.COMPLETED),
        gson.toJson(dockTagList));

    verify(dockTagPersisterService, times(1))
        .getDockTagsByDeliveriesAndStatuses(
            deliveryCaptor.capture(), instructionStatusCaptor.capture());
    assertEquals(deliveryCaptor.getValue().get(0).longValue(), 1234567L);
    assertTrue(instructionStatusCaptor.getValue().get(0).equals(InstructionStatus.COMPLETED));
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
        dockTagService.completeDockTags(completeDockTagRequest, httpHeaders);
    assertNotNull(completeDockTagResponse.getFailed());
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
        dockTagService.completeDockTags(completeDockTagRequest, httpHeaders);
    assertTrue(CollectionUtils.isEmpty(completeDockTagResponse.getFailed()));
    assertNotNull(completeDockTagResponse.getSuccess());

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
        dockTagService.completeDockTags(completeDockTagRequest, httpHeaders);
    assertTrue(CollectionUtils.isEmpty(completeDockTagResponse.getFailed()));
    assertNotNull(completeDockTagResponse.getSuccess());

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
        dockTagService.completeDockTags(completeDockTagRequest, httpHeaders);
    assertTrue(CollectionUtils.isEmpty(completeDockTagResponse.getFailed()));
    assertNotNull(completeDockTagResponse.getSuccess());

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
        dockTagService.completeDockTags(completeDockTagRequest, httpHeaders);

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
        dockTagService.completeDockTags(completeDockTagRequest, httpHeaders);

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
        dockTagService.completeDockTags(completeDockTagRequest, httpHeaders);

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
  public void createNonConDockTag() throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    when(accInstructionService.createNonConDockTag(any(), any(), any()))
        .thenReturn(new InstructionResponseImplNew());
    dockTagService.createDockTag(
        CreateDockTagRequest.builder()
            .deliveryNumber(1234567L)
            .doorNumber("101")
            .mappedPbylArea("PTR001")
            .build(),
        headers);
    ArgumentCaptor<InstructionRequest> instructionRequestArgumentCaptor =
        ArgumentCaptor.forClass(InstructionRequest.class);
    verify(accInstructionService, times(1))
        .createNonConDockTag(instructionRequestArgumentCaptor.capture(), eq(headers), eq("PTR001"));
    assertEquals(instructionRequestArgumentCaptor.getValue().getDoorNumber(), "101");
    assertEquals(instructionRequestArgumentCaptor.getValue().getDeliveryNumber(), "1234567");
  }

  @Test
  public void testReceiveNonConDockTag_HappyFlow() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getNonConDockTag());
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenReturn(deliveryDetailsJson);
    when(locationService.getLocationInfoForPbylDockTag(anyString()))
        .thenReturn(locationResponseForPbylLocation);
    ReceiveNonConDockTagResponse receiveNonConDockTagResponse =
        dockTagService.receiveNonConDockTag(dockTagId, headers);
    assertEquals(
        receiveNonConDockTagResponse.getDelivery(),
        gsonForDate.fromJson(deliveryDetailsJson, DeliveryDetails.class));
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(1)).saveDockTag(dockTagArgumentCaptor.capture());
    verify(deliveryService, times(1)).getDeliveryByDeliveryNumber(anyLong(), any());
    verify(locationService, times(1)).getLocationInfoForPbylDockTag(eq("PTR001"));
    DockTag dockTag = dockTagArgumentCaptor.getValue();
    assertEquals(dockTag.getDockTagStatus(), InstructionStatus.UPDATED);
    assertEquals(
        dockTag.getLastChangedUserId(), headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void testReceiveNonConDockTag_Exception_NotFound() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId))).thenReturn(null);
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenReturn(deliveryDetailsJson);
    when(locationService.getLocationInfoForPbylDockTag(anyString()))
        .thenReturn(locationResponseForPbylLocation);
    ReceiveNonConDockTagResponse receiveNonConDockTagResponse =
        dockTagService.receiveNonConDockTag(dockTagId, headers);
    assertEquals(
        JacksonParser.writeValueAsString(receiveNonConDockTagResponse.getDelivery()),
        deliveryDetailsJson);
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(0)).saveDockTag(dockTagArgumentCaptor.capture());
    verify(deliveryService, times(0)).getDeliveryByDeliveryNumber(anyLong(), any());
    verify(locationService, times(0)).getLocationInfoForPbylDockTag(eq("PTR001"));
    DockTag dockTag = dockTagArgumentCaptor.getValue();
    assertEquals(dockTag.getDockTagStatus(), InstructionStatus.UPDATED);
    assertEquals(
        dockTag.getLastChangedUserId(), headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveNonConDockTag_Exception_Completed() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getCompletedNonConDockTag());
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenReturn(deliveryDetailsJson);
    when(locationService.getLocationInfoForPbylDockTag(anyString()))
        .thenReturn(locationResponseForPbylLocation);
    ReceiveNonConDockTagResponse receiveNonConDockTagResponse =
        dockTagService.receiveNonConDockTag(dockTagId, headers);
    assertEquals(
        JacksonParser.writeValueAsString(receiveNonConDockTagResponse.getDelivery()),
        deliveryDetailsJson);
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(0)).saveDockTag(dockTagArgumentCaptor.capture());
    verify(deliveryService, times(0)).getDeliveryByDeliveryNumber(anyLong(), any());
    verify(locationService, times(0)).getLocationInfoForPbylDockTag(eq("PTR001"));
    DockTag dockTag = dockTagArgumentCaptor.getValue();
    assertEquals(dockTag.getDockTagStatus(), InstructionStatus.UPDATED);
    assertEquals(
        dockTag.getLastChangedUserId(), headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveNonConDockTag_Exception_NotNonCon() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getCompletedNonConDockTag());
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenReturn(deliveryDetailsJson);
    when(locationService.getLocationInfoForPbylDockTag(anyString()))
        .thenReturn(locationResponseForPbylLocation);
    ReceiveNonConDockTagResponse receiveNonConDockTagResponse =
        dockTagService.receiveNonConDockTag(dockTagId, headers);
    assertEquals(
        JacksonParser.writeValueAsString(receiveNonConDockTagResponse.getDelivery()),
        deliveryDetailsJson);
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(0)).saveDockTag(dockTagArgumentCaptor.capture());
    verify(deliveryService, times(0)).getDeliveryByDeliveryNumber(anyLong(), any());
    verify(locationService, times(0)).getLocationInfoForPbylDockTag(eq("PTR001"));
    DockTag dockTag = dockTagArgumentCaptor.getValue();
    assertEquals(dockTag.getDockTagStatus(), InstructionStatus.UPDATED);
    assertEquals(
        dockTag.getLastChangedUserId(), headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testReceiveNonConDockTag_Exception_FetchDeliveryError() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getNonConDockTag());
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenThrow(new ReceivingException("Some exception"));
    when(locationService.getLocationInfoForPbylDockTag(anyString()))
        .thenReturn(locationResponseForPbylLocation);
    ReceiveNonConDockTagResponse receiveNonConDockTagResponse =
        dockTagService.receiveNonConDockTag(dockTagId, headers);
    assertEquals(
        JacksonParser.writeValueAsString(receiveNonConDockTagResponse.getDelivery()),
        deliveryDetailsJson);
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(0)).saveDockTag(dockTagArgumentCaptor.capture());
    verify(deliveryService, times(1)).getDeliveryByDeliveryNumber(anyLong(), any());
    verify(locationService, times(0)).getLocationInfoForPbylDockTag(eq("PTR001"));
    DockTag dockTag = dockTagArgumentCaptor.getValue();
    assertEquals(dockTag.getDockTagStatus(), InstructionStatus.UPDATED);
    assertEquals(
        dockTag.getLastChangedUserId(), headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
  }

  @Test
  public void testCompleteBulkDockTagsForMultipleDeliveries_NoDockTagInDb() {
    DockTag dockTag = MockDockTag.getDockTag();
    List<String> docktags = Arrays.asList(dockTag.getDockTagId());

    CompleteDockTagRequest completeDockTagRequest =
        CompleteDockTagRequest.builder()
            .deliveryNumber(1234567L)
            .deliveryStatus("WRK")
            .docktags(docktags)
            .build();
    CompleteDockTagRequestsList completeDockTagRequestsList = new CompleteDockTagRequestsList();
    completeDockTagRequestsList.setList(new ArrayList<>());
    completeDockTagRequestsList.getList().add(completeDockTagRequest);

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(new ArrayList<>());
    List<CompleteDockTagResponse> completeDockTagResponses =
        dockTagService.completeDockTagsForGivenDeliveries(completeDockTagRequestsList, httpHeaders);
    assertNotNull(completeDockTagResponses.get(0).getFailed());
    assertTrue(CollectionUtils.isEmpty(completeDockTagResponses.get(0).getSuccess()));
  }

  @Test
  public void testCompleteBulkDockTagsForMultipleDeliveries_Success() throws ReceivingException {
    DockTag dockTag = MockDockTag.getDockTag();
    List<String> docktags = Arrays.asList(dockTag.getDockTagId());
    CompleteDockTagRequest completeDockTagRequest =
        CompleteDockTagRequest.builder()
            .deliveryNumber(1234567L)
            .deliveryStatus("WRK")
            .docktags(docktags)
            .build();
    CompleteDockTagRequestsList completeDockTagRequestsList = new CompleteDockTagRequestsList();
    completeDockTagRequestsList.setList(new ArrayList<>());
    completeDockTagRequestsList.getList().add(completeDockTagRequest);

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(dockTag);
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(dockTagList);
    List<CompleteDockTagResponse> completeDockTagResponses =
        dockTagService.completeDockTagsForGivenDeliveries(completeDockTagRequestsList, httpHeaders);
    assertTrue(CollectionUtils.isEmpty(completeDockTagResponses.get(0).getFailed()));
    assertNotNull(completeDockTagResponses.get(0).getSuccess());

    verify(inventoryService, times(1)).deleteContainer(dockTag.getDockTagId(), httpHeaders);

    verify(dockTagPersisterService, times(1)).saveAllDockTags(dockTagCaptor.capture());
    List<DockTag> dockTags = dockTagCaptor.getValue();
    assertEquals(dockTags.size(), 1);
    assertEquals(dockTags.get(0).getDockTagStatus(), InstructionStatus.COMPLETED);
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test
  public void testCompleteBulkDockTagsDeliveryGivenMultipleDeliveries_StatusOPN() {
    DockTag dockTag = MockDockTag.getDockTag();
    List<String> docktags = Arrays.asList(dockTag.getDockTagId());

    CompleteDockTagRequest completeDockTagRequest =
        CompleteDockTagRequest.builder()
            .deliveryNumber(1234567L)
            .deliveryStatus("OPN")
            .docktags(docktags)
            .build();
    CompleteDockTagRequestsList completeDockTagRequestsList = new CompleteDockTagRequestsList();
    completeDockTagRequestsList.setList(new ArrayList<>());
    completeDockTagRequestsList.getList().add(completeDockTagRequest);

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(dockTag);
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(dockTagList);
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(0);
    doReturn(receiptSummaryEachesResponse)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());

    List<CompleteDockTagResponse> completeDockTagResponses =
        dockTagService.completeDockTagsForGivenDeliveries(completeDockTagRequestsList, httpHeaders);
    assertTrue(CollectionUtils.isEmpty(completeDockTagResponses.get(0).getFailed()));
    assertNotNull(completeDockTagResponses.get(0).getSuccess());

    verify(inventoryService, times(1)).deleteContainer(dockTag.getDockTagId(), httpHeaders);

    verify(dockTagPersisterService, times(1)).saveAllDockTags(dockTagCaptor.capture());
    List<DockTag> dockTags = dockTagCaptor.getValue();
    assertEquals(dockTags.size(), 1);
    assertEquals(dockTags.get(0).getDockTagStatus(), InstructionStatus.COMPLETED);
  }

  @Test
  public void testCompleteBulkDockTagsForGivenDeliveries_OneAlreadyCompleted()
      throws ReceivingException {
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
    CompleteDockTagRequestsList completeDockTagRequestsList = new CompleteDockTagRequestsList();
    completeDockTagRequestsList.setList(new ArrayList<>());
    completeDockTagRequestsList.getList().add(completeDockTagRequest);

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(dockTag);
    dockTagList.add(completeDockTag);
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(dockTagList);
    List<CompleteDockTagResponse> completeDockTagResponses =
        dockTagService.completeDockTagsForGivenDeliveries(completeDockTagRequestsList, httpHeaders);

    assertTrue(CollectionUtils.isEmpty(completeDockTagResponses.get(0).getFailed()));
    assertEquals(completeDockTagResponses.get(0).getSuccess().size(), 2);

    verify(inventoryService, times(1)).deleteContainer(dockTag.getDockTagId(), httpHeaders);

    verify(dockTagPersisterService, times(1)).saveAllDockTags(dockTagCaptor.capture());
    List<DockTag> dockTags = dockTagCaptor.getValue();
    assertEquals(dockTags.size(), 2);

    assertEquals(dockTags.get(0).getDockTagStatus(), InstructionStatus.COMPLETED);
    assertEquals(dockTags.get(1).getDockTagStatus(), InstructionStatus.COMPLETED);
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test
  public void testCompleteBulkDockTagsForGivenDeliveries_OneNotPresentInDB()
      throws ReceivingException {
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
    CompleteDockTagRequestsList completeDockTagRequestsList = new CompleteDockTagRequestsList();
    completeDockTagRequestsList.setList(new ArrayList<>());
    completeDockTagRequestsList.getList().add(completeDockTagRequest);

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(dockTag);
    when(dockTagPersisterService.getDockTagsByDockTagIds(docktags)).thenReturn(dockTagList);
    List<CompleteDockTagResponse> completeDockTagResponses =
        dockTagService.completeDockTagsForGivenDeliveries(completeDockTagRequestsList, httpHeaders);

    assertEquals(
        completeDockTagResponses.get(0).getFailed().get(0), completeDockTag.getDockTagId());
    assertEquals(completeDockTagResponses.get(0).getSuccess().get(0), dockTag.getDockTagId());

    verify(inventoryService, times(1)).deleteContainer(dockTag.getDockTagId(), httpHeaders);

    verify(dockTagPersisterService, times(1)).saveAllDockTags(dockTagCaptor.capture());
    List<DockTag> dockTags = dockTagCaptor.getValue();
    assertEquals(dockTags.size(), 1);

    assertEquals(dockTags.get(0).getDockTagStatus(), InstructionStatus.COMPLETED);
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test
  public void testCompleteBulkDockTagsForGivenDeliveries_OneInventoryException()
      throws ReceivingException {
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
    CompleteDockTagRequestsList completeDockTagRequestsList = new CompleteDockTagRequestsList();
    completeDockTagRequestsList.setList(new ArrayList<>());
    completeDockTagRequestsList.getList().add(completeDockTagRequest);

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

    List<CompleteDockTagResponse> completeDockTagResponses =
        dockTagService.completeDockTagsForGivenDeliveries(completeDockTagRequestsList, httpHeaders);

    assertEquals(completeDockTagResponses.get(0).getFailed().get(0), dockTag1.getDockTagId());
    assertEquals(completeDockTagResponses.get(0).getSuccess().get(0), dockTag.getDockTagId());

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

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Maximum allowed docktags to complete is 15")
  public void testCompleteBulkDockTagsForGivenDeliveries_MoreThanAllowedDockTags() {

    List<String> docktagList = new ArrayList<>();
    List<DockTag> docktagDb = new ArrayList<>();

    for (int index = 1; index <= 16; index++) {
      String dockTagId = "c3298700000000000000000" + index;
      docktagList.add(dockTagId);
      DockTag dockTag = MockDockTag.getDockTag();
      dockTag.setDockTagId(dockTagId);
      dockTag.setDeliveryNumber(1234567L);
      docktagDb.add(dockTag);
    }

    CompleteDockTagRequest completeDockTagRequest =
        CompleteDockTagRequest.builder()
            .deliveryNumber(1234567L)
            .deliveryStatus("WRK")
            .docktags(docktagList)
            .build();
    CompleteDockTagRequestsList completeDockTagRequestsList = new CompleteDockTagRequestsList();
    completeDockTagRequestsList.setList(new ArrayList<>());
    completeDockTagRequestsList.getList().add(completeDockTagRequest);

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

    when(dockTagPersisterService.getDockTagsByDockTagIds(docktagList)).thenReturn(docktagDb);
    List<CompleteDockTagResponse> completeDockTagResponses =
        dockTagService.completeDockTagsForGivenDeliveries(completeDockTagRequestsList, httpHeaders);
  }

  @Test
  public void testCompleteDockTag_isKotlinEnabled_On() throws ReceivingException {
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getDockTag());
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(0);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"COMPLETE\"}");
    doReturn(receiptSummaryEachesResponse)
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    when(deliveryService.completeDelivery(anyLong(), anyBoolean(), any()))
        .thenReturn(new DeliveryInfo());
    when(instructionService.hasOpenInstruction(anyLong())).thenReturn(Boolean.FALSE);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(IS_KOTLIN_CLIENT, "true");
    dockTagService.completeDockTag(dockTagId, httpHeaders);
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(
            eq(12340001L), eq(ReceivingUtils.getPendingDockTagStatus()));
    verify(inventoryService, times(1)).deleteContainer(eq(dockTagId), any());
    verify(deliveryService, times(1)).getDeliveryByDeliveryNumber(eq(12340001L), any());
    verify(deliveryService, times(1)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test
  public void testCompleteDockTag_RoboDepal() throws ReceivingException {
    HttpHeaders headers = new HttpHeaders();
    headers.set(ReceivingConstants.TENENT_FACLITYNUM, "32987");
    headers.set(ReceivingConstants.TENENT_COUNTRY_CODE, "us");
    headers.set(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "1a2bc3d4");
    headers.add(ReceivingConstants.DOCKTAG_EVENT_TIMESTAMP, "2024-04-05T18:26:33.418Z");
    headers.add(ReceivingConstants.USER_ID_HEADER_KEY, "Automation");
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId)))
        .thenReturn(MockDockTag.getDockTag());
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.ROBO_DEPAL_FEATURE_ENABLED))
        .thenReturn(true);
    when(appConfig.getRoboDepalUserId()).thenReturn("Automation");
    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(anyLong(), any()))
        .thenReturn(0);
    doNothing().when(inventoryService).deleteContainer(eq(dockTagId), any());
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"WRK\"}");
    when(instructionService.hasOpenInstruction(anyLong())).thenReturn(Boolean.FALSE);
    DockTag dockTag =
        gson.fromJson(dockTagService.completeDockTag(dockTagId, headers), DockTag.class);
    assertEquals(dockTag.getDockTagStatus(), InstructionStatus.COMPLETED);
    assertNotNull(dockTag.getCompleteTs());
    assertEquals(dockTag.getCompleteUserId(), "Automation");
    verify(dockTagPersisterService, times(1)).getDockTagByDockTagId(eq(dockTagId));
    verify(dockTagPersisterService, times(0))
        .getCountOfDockTagsByDeliveryAndStatuses(
            eq(12340001L), eq(ReceivingUtils.getPendingDockTagStatus()));
    verify(inventoryService, times(1)).deleteContainer(eq(dockTagId), any());
    verify(deliveryService, times(1)).getDeliveryByDeliveryNumber(eq(12340001L), any());
    verify(receiptService, times(0)).getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
    verify(instructionService, times(0)).hasOpenInstruction(anyLong());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void validateForMultiManifestDockTag_itemCollision() {
    DockTag dockTag = MockDockTag.getDockTag();
    Receipt receiptWithinIdleTime = MockReceipt.getReceipt();
    receiptWithinIdleTime.setCreateTs((new DateTime().minusHours(5)).toDate());
    when(configUtils.isFeatureFlagEnabled(
            eq(ReceivingConstants.FLOORLINE_ITEM_COLLISION_SUCCESS_RESPONSE_ENABLED)))
        .thenReturn(true);
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId2)))
        .thenReturn(MockDockTag.getDockTag2());
    when(dockTagPersisterService.getAttachedDockTagsByScannedLocation(eq(scannedLocation2)))
        .thenReturn(MockDockTag.getMultiManifestDockTags());
    when(receiptService.findLatestReceiptByDeliveryNumber(anyLong()))
        .thenReturn(receiptWithinIdleTime);
    when(labelDataService.findIfCommonItemExistsForDeliveries(anyList(), anyLong()))
        .thenReturn(Collections.singletonList(1234567890L));
    when(configUtils.getCcmConfigValue(anyInt(), anyString())).thenReturn(gson.toJsonTree(10));
    when(configUtils.isFeatureFlagEnabled(anyString())).thenReturn(Boolean.TRUE);
    when(configUtils.isFeatureFlagEnabled(
            ACCConstants.ENABLE_POSSIBLE_UPC_BASED_ITEM_COLLISION_MMR))
        .thenReturn(true);
    Pair<Long, String> itemNoPossibleUPC =
        new Pair<>(551705258l, MockACLMessageData.possibleUPCFirstItem());
    List<Pair<Long, String>> itemNoPossibleUPCPairForDeliveryNumber =
        Arrays.asList(itemNoPossibleUPC);
    when(labelDataService.findItemPossibleUPCPairsForDeliveryNumber(anyLong()))
        .thenReturn(itemNoPossibleUPCPairForDeliveryNumber);
    List<String> activeDeliveriesPossibleUPCsList =
        Arrays.asList(MockACLMessageData.possibleUPCSecondItem());
    when(labelDataService.findPossibleUPCsForDeliveryNumbersIn(anyList()))
        .thenReturn(activeDeliveriesPossibleUPCsList);
    dockTagService.validateForMultiManifestDockTag(dockTag, "WS0003", scannedLocation2);
  }

  @Test
  public void validateForMultiManifestDockTag_withoutItemCollision() {
    DockTag dockTag = MockDockTag.getDockTag();
    Receipt receiptWithinIdleTime = MockReceipt.getReceipt();
    receiptWithinIdleTime.setCreateTs((new DateTime().minusHours(5)).toDate());
    when(configUtils.isFeatureFlagEnabled(
            eq(ReceivingConstants.FLOORLINE_ITEM_COLLISION_SUCCESS_RESPONSE_ENABLED)))
        .thenReturn(true);
    when(dockTagPersisterService.getDockTagByDockTagId(eq(dockTagId2)))
        .thenReturn(MockDockTag.getDockTag2());
    when(dockTagPersisterService.getAttachedDockTagsByScannedLocation(eq(scannedLocation2)))
        .thenReturn(MockDockTag.getMultiManifestDockTags());
    when(receiptService.findLatestReceiptByDeliveryNumber(anyLong()))
        .thenReturn(receiptWithinIdleTime);
    when(configUtils.getCcmConfigValue(anyInt(), anyString())).thenReturn(gson.toJsonTree(10));
    when(configUtils.isFeatureFlagEnabled(anyString())).thenReturn(Boolean.TRUE);
    when(configUtils.isFeatureFlagEnabled(
            ACCConstants.ENABLE_POSSIBLE_UPC_BASED_ITEM_COLLISION_MMR))
        .thenReturn(true);
    Pair<Long, String> itemNoPossibleUPC =
        new Pair<>(551705258l, MockACLMessageData.possibleUPCThirdItem());
    List<Pair<Long, String>> itemNoPossibleUPCPairForDeliveryNumber =
        Arrays.asList(itemNoPossibleUPC);
    when(labelDataService.findItemPossibleUPCPairsForDeliveryNumber(anyLong()))
        .thenReturn(itemNoPossibleUPCPairForDeliveryNumber);
    List<String> activeDeliveriesPossibleUPCsList =
        Arrays.asList(MockACLMessageData.possibleUPCSecondItem());
    when(labelDataService.findPossibleUPCsForDeliveryNumbersIn(anyList()))
        .thenReturn(activeDeliveriesPossibleUPCsList);
    dockTagService.validateForMultiManifestDockTag(dockTag, "WS0003", scannedLocation2);
    verify(receiptService, times(2)).findLatestReceiptByDeliveryNumber(anyLong());
  }

  @Test
  public void testReceiveUniversalTagException() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    try {
      dockTagService.receiveUniversalTag("123434", "TEST", httpHeaders);
    } catch (Exception ex) {
      Assert.assertTrue(ex instanceof ReceivingInternalException);
    }
  }

  @Test
  void testSearchDockTagById() {
    String dockTagId = "b039300000200000001456458";
    DockTag dockTag = new DockTag();
    dockTag.setDockTagId(dockTagId);
    dockTag.setDeliveryNumber(96489814L);
    dockTag.setDockTagStatus(InstructionStatus.COMPLETED);
    dockTag.setDockTagType(DockTagType.NON_CON);

    when(dockTagPersisterService.getDockTagByDockTagId(any(String.class))).thenReturn(dockTag);

    DockTagDTO dockTagDTO = dockTagService.searchDockTagById(dockTagId);
    assertEquals(dockTag.getDockTagId(), dockTagDTO.getDockTagId());
    assertEquals(dockTag.getDeliveryNumber(), dockTagDTO.getDeliveryNumber());
    assertEquals(dockTag.getDockTagStatus(), dockTagDTO.getDockTagStatus());
    assertEquals(dockTag.getDockTagType(), dockTagDTO.getDockTagType());
  }
}
