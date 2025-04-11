package com.walmart.move.nim.receiving.core.service;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.OpenDockTagCount;
import com.walmart.move.nim.receiving.core.model.docktag.CompleteDockTagRequest;
import com.walmart.move.nim.receiving.core.model.docktag.CompleteDockTagRequestsList;
import com.walmart.move.nim.receiving.core.model.docktag.CreateDockTagRequest;
import com.walmart.move.nim.receiving.core.model.docktag.ReceiveDockTagRequest;
import com.walmart.move.nim.receiving.core.model.docktag.SearchDockTagRequest;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DockTagType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DockTagServiceImplTest extends ReceivingTestBase {

  @InjectMocks private DockTagServiceImpl dockTagService;
  @Mock private ContainerService containerServ;
  @Mock private ContainerRepository containerRepo;
  @Mock private DockTagPersisterService dockTagPersisterService;
  private PageRequest pageReq;
  @Mock private DeliveryService deliveryService;
  @Mock private ReceiptService receiptService;
  @Mock private InventoryService inventoryService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Captor private ArgumentCaptor<List<DockTag>> dockTagCaptor;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    pageReq = PageRequest.of(0, 10);
  }

  @BeforeMethod
  public void beforeMethod() {
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any());
  }

  @AfterMethod
  public void resetMocks() {
    reset(dockTagPersisterService);
    reset(deliveryService);
    reset(receiptService);
    reset(inventoryService);
  }

  public DockTag getDockTag() {
    DockTag dockTag = new DockTag();
    dockTag.setCreateUserId("sysadmin");
    dockTag.setCreateTs(new Date());
    dockTag.setDeliveryNumber(12340001L);
    dockTag.setDockTagId("c32987000000000000000001");
    dockTag.setDockTagStatus(InstructionStatus.CREATED);
    dockTag.setFacilityNum(32987);
    dockTag.setFacilityCountryCode("US");
    return dockTag;
  }

  @Test
  public void testPurge() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    DockTag dockTag = getDockTag();
    dockTag.setId(1L);
    dockTag.setCreateTs(cal.getTime());

    DockTag dockTag1 = getDockTag();
    dockTag1.setId(10L);
    dockTag1.setCreateTs(cal.getTime());

    when(dockTagPersisterService.getDockTagsByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(dockTag, dockTag1));
    doNothing().when(dockTagPersisterService).deleteAllDockTags(any());
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.DOCK_TAG)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = dockTagService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 10L);
  }

  @Test
  public void testPurgeWithNoDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    DockTag dockTag = getDockTag();
    dockTag.setId(1L);
    dockTag.setCreateTs(cal.getTime());

    DockTag dockTag1 = getDockTag();
    dockTag1.setId(10L);
    dockTag1.setCreateTs(cal.getTime());

    when(dockTagPersisterService.getDockTagsByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(dockTag, dockTag1));
    doNothing().when(dockTagPersisterService).deleteAllDockTags(any());
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.DOCK_TAG)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = dockTagService.purge(purgeData, pageReq, 90);
    assertEquals(lastDeletedId, 0L);
  }

  @Test
  public void testPurgeWithFewDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    DockTag dockTag = getDockTag();
    dockTag.setId(1L);
    dockTag.setCreateTs(cal.getTime());

    DockTag dockTag1 = getDockTag();
    dockTag1.setId(10L);
    dockTag1.setCreateTs(new Date());

    when(dockTagPersisterService.getDockTagsByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(dockTag, dockTag1));
    doNothing().when(dockTagPersisterService).deleteAllDockTags(any());
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.DOCK_TAG)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = dockTagService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 1L);
  }

  @Test
  public void testAutoCompleteDockTagNoPendingDockTag() throws ReceivingException {
    int autoDockTagCompleteBeforeHours = 48;
    int facilityNumber = 32987;

    TenantContext.setFacilityNum(facilityNumber);
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -autoDockTagCompleteBeforeHours);
    Date fromDate = cal.getTime();

    when(tenantSpecificConfigReader.getCcmConfigValue(
            eq(String.valueOf(facilityNumber)),
            eq(ReceivingConstants.AUTO_DOCK_TAG_COMPLETE_HOURS)))
        .thenReturn(new JsonParser().parse("48"));
    when(dockTagPersisterService.getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), any(), any()))
        .thenReturn(null);
    when(deliveryService.completeDelivery(anyLong(), anyBoolean(), any()))
        .thenReturn(new DeliveryInfo());

    dockTagService.autoCompleteDocks(10);

    ArgumentCaptor<Date> dateCaptor = ArgumentCaptor.forClass(Date.class);
    verify(dockTagPersisterService, times(1))
        .getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), dateCaptor.capture(), any());
    assertEquals(fromDate.getDate(), dateCaptor.getValue().getDate());

    verify(dockTagPersisterService, times(0)).saveAllDockTags(anyList());
    verify(inventoryService, times(0)).deleteContainer(anyString(), any());
    verify(deliveryService, times(0)).getDeliveryByDeliveryNumber(any(), any());
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test
  public void testAutoCompleteDockTagNoPendingDockTagEmptyList() throws ReceivingException {
    int facilityNumber = 32987;
    TenantContext.setFacilityNum(facilityNumber);

    when(tenantSpecificConfigReader.getCcmConfigValue(
            eq(String.valueOf(facilityNumber)),
            eq(ReceivingConstants.AUTO_DOCK_TAG_COMPLETE_HOURS)))
        .thenReturn(new JsonParser().parse("48"));
    when(dockTagPersisterService.getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), any(), any()))
        .thenReturn(new ArrayList<>());
    when(deliveryService.completeDelivery(anyLong(), anyBoolean(), any()))
        .thenReturn(new DeliveryInfo());

    dockTagService.autoCompleteDocks(10);
    verify(dockTagPersisterService, times(1))
        .getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), any(), any());

    verify(dockTagPersisterService, times(0)).saveAllDockTags(anyList());
    verify(inventoryService, times(0)).deleteContainer(anyString(), any());
    verify(deliveryService, times(0)).getDeliveryByDeliveryNumber(any(), any());
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test
  public void testAutoCompleteDockTag() throws ReceivingException {
    int facilityNumber = 32987;
    TenantContext.setFacilityNum(facilityNumber);

    List<DockTag> dockTagList = new ArrayList<>();
    // 2 dock tag of same delivery
    DockTag dockTag = getDockTag();
    dockTagList.add(dockTag);
    dockTag = getDockTag();
    dockTag.setDockTagId("c32987000000000000000002");
    dockTagList.add(dockTag);

    // 1 docktag of different delivery of different facility
    dockTag = getDockTag();
    dockTag.setDockTagId("c32987000000000000000003");
    dockTag.setDeliveryNumber(43210002L);
    dockTag.setFacilityNum(32818);
    dockTagList.add(dockTag);

    when(tenantSpecificConfigReader.getCcmConfigValue(
            eq(String.valueOf(facilityNumber)),
            eq(ReceivingConstants.AUTO_DOCK_TAG_COMPLETE_HOURS)))
        .thenReturn(new JsonParser().parse("48"));

    when(dockTagPersisterService.getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), any(), any()))
        .thenReturn(dockTagList);

    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(
            any(), eq(ReceivingUtils.getPendingDockTagStatus())))
        .thenReturn(0);

    when(deliveryService.getDeliveryByDeliveryNumber(eq(43210002L), any()))
        .thenReturn("{\"deliveryNumber\":43210002,\"deliveryStatus\":\"WRK\"}");

    when(deliveryService.getDeliveryByDeliveryNumber(eq(12340001L), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"OPN\"}");

    when(deliveryService.completeDelivery(anyLong(), anyBoolean(), any()))
        .thenReturn(new DeliveryInfo());

    dockTagService.autoCompleteDocks(10);

    verify(dockTagPersisterService, times(1))
        .getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), any(), any());

    verify(inventoryService, times(3)).deleteContainer(any(), any());
    ArgumentCaptor<HttpHeaders> httpHeadersCaptor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(inventoryService, times(1))
        .deleteContainer(eq("c32987000000000000000001"), httpHeadersCaptor.capture());
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_COUNTRY_CODE), "US");
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_FACLITYNUM), "32987");
    verify(inventoryService, times(1))
        .deleteContainer(eq("c32987000000000000000002"), httpHeadersCaptor.capture());
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_COUNTRY_CODE), "US");
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_FACLITYNUM), "32987");
    verify(inventoryService, times(1))
        .deleteContainer(eq("c32987000000000000000003"), httpHeadersCaptor.capture());

    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_COUNTRY_CODE), "US");
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_FACLITYNUM), "32818");

    verify(dockTagPersisterService, times(1)).saveAllDockTags(dockTagCaptor.capture());
    assertEquals(dockTagCaptor.getValue().size(), 3);
    dockTagCaptor
        .getValue()
        .forEach(
            dt -> {
              assertEquals(dt.getDockTagStatus(), InstructionStatus.COMPLETED);
              assertNotNull(dt.getCompleteTs());
              assertEquals(
                  dt.getCompleteUserId(),
                  httpHeadersCaptor.getValue().getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
            });

    verify(deliveryService, times(2)).getDeliveryByDeliveryNumber(any(), any());
    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(eq(12340001L), httpHeadersCaptor.capture());
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_COUNTRY_CODE), "US");
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_FACLITYNUM), "32987");
    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(eq(43210002L), httpHeadersCaptor.capture());
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_COUNTRY_CODE), "US");
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_FACLITYNUM), "32818");

    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList());
    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(
            12340001L, ReceivingUtils.getPendingDockTagStatus());
    verify(deliveryService, times(1)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test
  public void testAutoCompleteDockTag_LastDockTag_ErrorFromCompleteDelivery()
      throws ReceivingException {

    int facilityNumber = 32987;
    TenantContext.setFacilityNum(facilityNumber);

    List<DockTag> dockTagList = new ArrayList<>();
    // 2 dock tag of same delivery
    DockTag dockTag = getDockTag();
    dockTagList.add(dockTag);
    dockTag = getDockTag();
    dockTag.setDockTagId("c32987000000000000000002");
    dockTagList.add(dockTag);

    // 1 docktag of different delivery of different facility
    dockTag = getDockTag();
    dockTag.setDockTagId("c32987000000000000000003");
    dockTag.setDeliveryNumber(43210002L);
    dockTag.setFacilityNum(32818);
    dockTagList.add(dockTag);

    when(tenantSpecificConfigReader.getCcmConfigValue(
            eq(String.valueOf(facilityNumber)),
            eq(ReceivingConstants.AUTO_DOCK_TAG_COMPLETE_HOURS)))
        .thenReturn(new JsonParser().parse("48"));

    when(dockTagPersisterService.getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), any(), any()))
        .thenReturn(dockTagList);

    when(dockTagPersisterService.getCountOfDockTagsByDeliveryAndStatuses(
            any(), eq(ReceivingUtils.getPendingDockTagStatus())))
        .thenReturn(0);

    when(deliveryService.getDeliveryByDeliveryNumber(eq(43210002L), any()))
        .thenReturn("{\"deliveryNumber\":43210002,\"deliveryStatus\":\"WRK\"}");

    when(deliveryService.getDeliveryByDeliveryNumber(eq(12340001L), any()))
        .thenReturn("{\"deliveryNumber\":12340001,\"deliveryStatus\":\"OPN\"}");
    when(deliveryService.completeDelivery(anyLong(), anyBoolean(), any()))
        .thenThrow(new ReceivingException("Error", HttpStatus.INTERNAL_SERVER_ERROR));

    dockTagService.autoCompleteDocks(10);

    verify(dockTagPersisterService, times(1))
        .getDockTagsByDockTagStatusesAndCreateTsLessThan(
            eq(ReceivingUtils.getPendingDockTagStatus()), any(), any());

    verify(inventoryService, times(3)).deleteContainer(any(), any());
    ArgumentCaptor<HttpHeaders> httpHeadersCaptor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(inventoryService, times(1))
        .deleteContainer(eq("c32987000000000000000001"), httpHeadersCaptor.capture());
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_COUNTRY_CODE), "US");
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_FACLITYNUM), "32987");
    verify(inventoryService, times(1))
        .deleteContainer(eq("c32987000000000000000002"), httpHeadersCaptor.capture());
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_COUNTRY_CODE), "US");
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_FACLITYNUM), "32987");
    verify(inventoryService, times(1))
        .deleteContainer(eq("c32987000000000000000003"), httpHeadersCaptor.capture());

    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_COUNTRY_CODE), "US");
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_FACLITYNUM), "32818");

    verify(dockTagPersisterService, times(1)).saveAllDockTags(dockTagCaptor.capture());
    assertEquals(dockTagCaptor.getValue().size(), 3);
    dockTagCaptor
        .getValue()
        .forEach(
            dt -> {
              assertEquals(dt.getDockTagStatus(), InstructionStatus.COMPLETED);
              assertNotNull(dt.getCompleteTs());
              assertEquals(
                  dt.getCompleteUserId(),
                  httpHeadersCaptor.getValue().getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
            });

    verify(deliveryService, times(2)).getDeliveryByDeliveryNumber(any(), any());
    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(eq(12340001L), httpHeadersCaptor.capture());
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_COUNTRY_CODE), "US");
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_FACLITYNUM), "32987");
    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(eq(43210002L), httpHeadersCaptor.capture());
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_COUNTRY_CODE), "US");
    assertEquals(
        httpHeadersCaptor.getValue().getFirst(ReceivingConstants.TENENT_FACLITYNUM), "32818");

    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(anyLong(), anyList());
    verify(dockTagPersisterService, times(1))
        .getCountOfDockTagsByDeliveryAndStatuses(
            12340001L, ReceivingUtils.getPendingDockTagStatus());
    verify(deliveryService, times(1)).completeDelivery(anyLong(), anyBoolean(), any());
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Tenant-32987 not supported")
  public void testCreateDockTag() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
    dockTagService.createDockTag(
        CreateDockTagRequest.builder().deliveryNumber(1234567L).doorNumber("100").build(),
        MockHttpHeaders.getHeaders());
  }

  @Test
  public void testGetDockTagById() {
    when(dockTagPersisterService.getDockTagByDockTagId(anyString())).thenReturn(getDockTag());
    assertNotNull(dockTagService.getDockTagById("a32818000000001123"));
  }

  @Test
  public void testGetAllDockTagsForTenant() {
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(getDockTag());
    when(dockTagPersisterService.getDockTagsByStatuses(anyList())).thenReturn(dockTagList);

    assertEquals(
        dockTagService.searchAllDockTagForGivenTenant(InstructionStatus.CREATED), dockTagList);

    verify(dockTagPersisterService, times(1)).getDockTagsByStatuses(anyList());
  }

  @Test
  public void testGetAllDockTagsForTenant_forAllStatus() {
    List<DockTag> dockTagList = new ArrayList<>();
    dockTagList.add(getDockTag());
    when(dockTagPersisterService.getDockTagsByStatuses(anyList())).thenReturn(dockTagList);

    assertEquals(
        dockTagService.searchAllDockTagForGivenTenant(InstructionStatus.COMPLETED), dockTagList);

    verify(dockTagPersisterService, times(1)).getDockTagsByStatuses(anyList());
  }

  @Test
  public void testCountAllDockTagsForTenant() {

    when(dockTagPersisterService.getCountOfDockTagsByStatuses(anyList())).thenReturn(15);
    OpenDockTagCount openDockTagCount = dockTagService.countDockTag(InstructionStatus.CREATED);
    assertEquals(openDockTagCount.getCount().intValue(), 15);

    verify(dockTagPersisterService, times(1)).getCountOfDockTagsByStatuses(anyList());
  }

  @Test
  public void testCountAllDockTagsForTenant_forAllStatus() {
    when(dockTagPersisterService.getCountOfDockTagsByStatuses(anyList())).thenReturn(25);
    OpenDockTagCount openDockTagCount = dockTagService.countDockTag(InstructionStatus.COMPLETED);
    assertEquals(openDockTagCount.getCount().intValue(), 25);
    verify(dockTagPersisterService, times(1)).getCountOfDockTagsByStatuses(anyList());
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void testValidateDockTagFromDb_ReceivingDataNotFoundException() {
    dockTagService.validateDockTagFromDb(
        null, "123456", "Error Message", MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testValidateDockTagFromDb_ReceivingBadDataException() {
    DockTag dockTag = getDockTag();
    dockTag.setCompleteTs(new Date());
    dockTagService.validateDockTagFromDb(
        dockTag, "123456", "Error Message", MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void tesSearchDockTag() {
    dockTagService.searchDockTag(new SearchDockTagRequest(), InstructionStatus.CREATED);
  }

  @Test
  public void tesCountOfOpenDockTags() {
    Integer response = dockTagService.countOfOpenDockTags(1L);
    assertNull(response);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void tesCreateDockTag() {
    dockTagService.createDockTag("DT12345", 1L, "sysadmin", DockTagType.ATLAS_RECEIVING);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void tesReceiveDockTag() {
    dockTagService.receiveDockTag(new ReceiveDockTagRequest(), MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testReceiveNonConDockTag() {
    dockTagService.receiveNonConDockTag("dockTagId", MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testCompleteDockTags() {
    dockTagService.completeDockTags(new CompleteDockTagRequest(), MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testCompleteDockTagsForGivenDeliveries() {
    dockTagService.completeDockTagsForGivenDeliveries(
        new CompleteDockTagRequestsList(), MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testReceiveUniversalTagException() {
    dockTagService.receiveUniversalTag("123434", "TEST", MockHttpHeaders.getHeaders());
  }
}
