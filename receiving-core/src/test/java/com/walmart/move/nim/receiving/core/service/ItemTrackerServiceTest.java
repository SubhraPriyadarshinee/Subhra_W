package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.ItemTracker;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.model.ItemTrackerRequest;
import com.walmart.move.nim.receiving.core.repositories.ItemTrackerRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ItemTrackerServiceTest extends ReceivingTestBase {
  @InjectMocks private ItemTrackerService itemTrackerService;
  @Mock private ItemTrackerRepository itemTrackerRepository;
  private Gson gson;
  private ItemTracker itemTracker, itemTrackerForPurgeTest, itemTrackerForPurgeTestNotToBeDeleted;
  private ItemTrackerRequest itemTrackerRequest;
  private ItemTrackerRequest itemTrackerRequestInvalid;
  private PageRequest pageReq;
  private PurgeData purgeData;

  @BeforeClass
  public void initMocksAndFields() throws IOException {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(9074);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setAdditionalParams(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    gson = new Gson();
    pageReq = PageRequest.of(0, 10);
    purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.CONTAINER_RLOG)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();
    String dataPathItemTracker =
        new File("../receiving-test/src/main/resources/json/ItemTracker.json").getCanonicalPath();
    itemTracker =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathItemTracker))), ItemTracker.class);
    itemTrackerForPurgeTest =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathItemTracker))), ItemTracker.class);
    itemTrackerForPurgeTestNotToBeDeleted =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathItemTracker))), ItemTracker.class);
    String dataPathItemTrackerRequest =
        new File("../receiving-test/src/main/resources/json/ItemTrackerRequest.json")
            .getCanonicalPath();
    itemTrackerRequest =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathItemTrackerRequest))),
            ItemTrackerRequest.class);
    String dataPathItemTrackerRequestInvalid =
        new File("../receiving-test/src/main/resources/json/ItemTrackerRequestInvalid.json")
            .getCanonicalPath();
    itemTrackerRequestInvalid =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathItemTrackerRequestInvalid))),
            ItemTrackerRequest.class);
  }

  @BeforeMethod
  public void reset() {
    Mockito.reset(itemTrackerRepository);
  }

  @Test
  public void testTrackItem() {
    when(itemTrackerRepository.save(any(ItemTracker.class))).thenReturn(itemTracker);
    itemTrackerService.trackItem(itemTrackerRequest);
    verify(itemTrackerRepository, times(1)).save(any(ItemTracker.class));
  }

  @Test
  public void testTrackItems() {
    when(itemTrackerRepository.saveAll(anyList()))
        .thenReturn(Collections.singletonList(itemTracker));
    itemTrackerService.trackItems(Collections.singletonList(itemTrackerRequest));
    verify(itemTrackerRepository, times(1)).saveAll(anyList());
  }

  @Test
  public void testTrackItemsForEmptyItemTrackerRequests() {
    itemTrackerService.trackItems(Collections.emptyList());
    verify(itemTrackerRepository, times(0)).saveAll(anyList());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Item cannot be tracked without proper reason code.")
  public void testTrackItemForInvalidReasonCode() {
    when(itemTrackerRepository.save(any(ItemTracker.class))).thenReturn(itemTracker);
    itemTrackerService.trackItem(itemTrackerRequestInvalid);
    verify(itemTrackerRepository, times(1)).save(any(ItemTracker.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Item cannot be tracked without proper reason code.")
  public void testTrackItemsForInvalidReasonCode() {
    when(itemTrackerRepository.saveAll(anyList()))
        .thenReturn(Collections.singletonList(itemTracker));
    itemTrackerService.trackItems(Collections.singletonList(itemTrackerRequestInvalid));
    verify(itemTrackerRepository, times(1)).saveAll(anyList());
  }

  @Test
  public void testGetTrackedItemByTrackingId() {
    when(itemTrackerRepository.findByTrackingId(any(String.class)))
        .thenReturn(Collections.singletonList(itemTracker));
    itemTrackerService.getTrackedItemByTrackingId("5512098217046");
    verify(itemTrackerRepository, times(1)).findByTrackingId(any(String.class));
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Item not found for trackingId=5512098217047")
  public void testGetTrackedItemByTrackingIdNotFound() {
    when(itemTrackerRepository.findByTrackingId(any(String.class)))
        .thenReturn(Collections.emptyList());
    itemTrackerService.getTrackedItemByTrackingId("5512098217047");
    verify(itemTrackerRepository, times(1)).findByTrackingId(any(String.class));
  }

  @Test
  public void testDeleteTrackedItemByTrackingId() {
    when(itemTrackerRepository.findByTrackingId(any(String.class)))
        .thenReturn(Collections.singletonList(itemTracker));
    doNothing().when(itemTrackerRepository).deleteByTrackingId(anyString());
    itemTrackerService.deleteTrackedItemByTrackingId("5512098217046");
    verify(itemTrackerRepository, times(1)).findByTrackingId(any(String.class));
    verify(itemTrackerRepository, times(1)).deleteByTrackingId(any(String.class));
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Item not found for trackingId=5512098217047")
  public void testDeleteTrackedItemByTrackingIdNotFound() {
    when(itemTrackerRepository.findByTrackingId(any(String.class)))
        .thenReturn(Collections.emptyList());
    itemTrackerService.deleteTrackedItemByTrackingId("5512098217047");
    verify(itemTrackerRepository, times(1)).findByTrackingId(any(String.class));
    verify(itemTrackerRepository, times(0)).deleteByTrackingId(any(String.class));
  }

  @Test
  public void testGetTrackedItemByGtin() {
    when(itemTrackerRepository.findByGtin(any(String.class)))
        .thenReturn(Collections.singletonList(itemTracker));
    itemTrackerService.getTrackedItemByGtin("00604015693198");
    verify(itemTrackerRepository, times(1)).findByGtin(any(String.class));
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Item not found for gtin=00604015693199")
  public void testGetTrackedItemByGtinNotFound() {
    when(itemTrackerRepository.findByGtin(any(String.class))).thenReturn(Collections.emptyList());
    itemTrackerService.getTrackedItemByGtin("00604015693199");
    verify(itemTrackerRepository, times(1)).findByGtin(any(String.class));
  }

  @Test
  public void testDeleteTrackedItemByGtin() {
    when(itemTrackerRepository.findByGtin(any(String.class)))
        .thenReturn(Collections.singletonList(itemTracker));
    doNothing().when(itemTrackerRepository).deleteByGtin(anyString());
    itemTrackerService.deleteTrackedItemByGtin("00604015693198");
    verify(itemTrackerRepository, times(1)).findByGtin(any(String.class));
    verify(itemTrackerRepository, times(1)).deleteByGtin(any(String.class));
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Item not found for gtin=00604015693199")
  public void testDeleteTrackedItemByGtinNotFound() {
    when(itemTrackerRepository.findByGtin(any(String.class))).thenReturn(Collections.emptyList());
    itemTrackerService.deleteTrackedItemByGtin("00604015693199");
    verify(itemTrackerRepository, times(1)).findByGtin(any(String.class));
    verify(itemTrackerRepository, times(0)).deleteByGtin(any(String.class));
  }

  @Test
  public void testPurge() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);
    itemTrackerForPurgeTest.setCreateTs(cal.getTime());

    when(itemTrackerRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(itemTrackerForPurgeTest));
    doNothing().when(itemTrackerRepository).deleteAll();
    long lastDeletedId = itemTrackerService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 1L);
  }

  @Test
  public void testPurgeWithNoDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);
    itemTrackerForPurgeTest.setCreateTs(cal.getTime());

    when(itemTrackerRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(itemTrackerForPurgeTest));
    doNothing().when(itemTrackerRepository).deleteAll();
    long lastDeletedId = itemTrackerService.purge(purgeData, pageReq, 90);
    assertEquals(lastDeletedId, 0L);
  }

  @Test
  public void testPurgeWithFewDataToDeleteBeforeDate() {
    Calendar cal1 = Calendar.getInstance();
    Calendar cal2 = Calendar.getInstance();
    cal1.add(Calendar.HOUR, -60 * 24);
    itemTrackerForPurgeTest.setCreateTs(cal1.getTime());
    cal2.add(Calendar.HOUR, -1 * 24);
    itemTrackerForPurgeTestNotToBeDeleted.setId(2L);
    itemTrackerForPurgeTestNotToBeDeleted.setCreateTs(cal2.getTime());

    when(itemTrackerRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(itemTrackerForPurgeTest, itemTrackerForPurgeTestNotToBeDeleted));
    doNothing().when(itemTrackerRepository).deleteAll();
    long lastDeletedId = itemTrackerService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 1L);
  }
}
