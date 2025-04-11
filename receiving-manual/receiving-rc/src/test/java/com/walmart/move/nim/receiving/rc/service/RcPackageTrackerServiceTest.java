package com.walmart.move.nim.receiving.rc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.rc.entity.PackageRLog;
import com.walmart.move.nim.receiving.rc.model.dto.request.PackageTrackerRequest;
import com.walmart.move.nim.receiving.rc.repositories.PackageRLogRepository;
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

public class RcPackageTrackerServiceTest extends ReceivingTestBase {
  @InjectMocks private RcPackageTrackerService packageTrackerService;
  @Mock private PackageRLogRepository packageRLogRepository;
  private Gson gson;
  private PackageRLog packageRLog, packageRLogForPurgeTest, packageRLogForPurgeTestNotToBeDeleted;
  private PackageTrackerRequest packageTrackerRequest;
  private PackageTrackerRequest packageTrackerRequestInvalid;
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
    String dataPathPackageRLog =
        new File("../../receiving-test/src/main/resources/json/RcPackageRLog.json")
            .getCanonicalPath();
    packageRLog =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathPackageRLog))), PackageRLog.class);
    packageRLogForPurgeTest =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathPackageRLog))), PackageRLog.class);
    packageRLogForPurgeTestNotToBeDeleted =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathPackageRLog))), PackageRLog.class);
    String dataPathPackageTrackerRequest =
        new File("../../receiving-test/src/main/resources/json/RcPackageTrackerRequest.json")
            .getCanonicalPath();
    packageTrackerRequest =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathPackageTrackerRequest))),
            PackageTrackerRequest.class);
    String dataPathPackageTrackerRequestInvalid =
        new File("../../receiving-test/src/main/resources/json/RcPackageTrackerRequestInvalid.json")
            .getCanonicalPath();
    packageTrackerRequestInvalid =
        gson.fromJson(
            new String(Files.readAllBytes(Paths.get(dataPathPackageTrackerRequestInvalid))),
            PackageTrackerRequest.class);
  }

  @BeforeMethod
  public void reset() {
    Mockito.reset(packageRLogRepository);
  }

  @Test
  public void testTrackPackageStatus() {
    when(packageRLogRepository.save(any(PackageRLog.class))).thenReturn(packageRLog);
    packageTrackerService.trackPackageStatus(packageTrackerRequest);
    verify(packageRLogRepository, times(1)).save(any(PackageRLog.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Package cannot be tracked without proper reason code.")
  public void testTrackPackageStatusForInvalidReasonCode() {
    when(packageRLogRepository.save(any(PackageRLog.class))).thenReturn(packageRLog);
    packageTrackerService.trackPackageStatus(packageTrackerRequestInvalid);
    verify(packageRLogRepository, times(1)).save(any(PackageRLog.class));
  }

  @Test
  public void testGetTrackedPackage() {
    when(packageRLogRepository.findByPackageBarCodeValue(any(String.class)))
        .thenReturn(Collections.singletonList(packageRLog));
    packageTrackerService.getTrackedPackage("5512098217046");
    verify(packageRLogRepository, times(1)).findByPackageBarCodeValue(any(String.class));
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Package not found for packageBarcodeValue=5512098217047")
  public void testGetTrackedPackageNotFound() {
    when(packageRLogRepository.findByPackageBarCodeValue(any(String.class)))
        .thenReturn(Collections.emptyList());
    packageTrackerService.getTrackedPackage("5512098217047");
    verify(packageRLogRepository, times(1)).findByPackageBarCodeValue(any(String.class));
  }

  @Test
  public void testDeleteTrackedPackage() {
    when(packageRLogRepository.findByPackageBarCodeValue(any(String.class)))
        .thenReturn(Collections.singletonList(packageRLog));
    doNothing().when(packageRLogRepository).deleteByPackageBarCodeValue(anyString());
    packageTrackerService.deleteTrackedPackage("5512098217046");
    verify(packageRLogRepository, times(1)).findByPackageBarCodeValue(any(String.class));
    verify(packageRLogRepository, times(1)).deleteByPackageBarCodeValue(any(String.class));
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Package not found for packageBarcodeValue=5512098217047")
  public void testDeleteTrackedPackageNotFound() {
    when(packageRLogRepository.findByPackageBarCodeValue(any(String.class)))
        .thenReturn(Collections.emptyList());
    packageTrackerService.deleteTrackedPackage("5512098217047");
    verify(packageRLogRepository, times(1)).findByPackageBarCodeValue(any(String.class));
    verify(packageRLogRepository, times(0)).deleteByPackageBarCodeValue(any(String.class));
  }

  @Test
  public void testPurge() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);
    packageRLogForPurgeTest.setCreateTs(cal.getTime());

    when(packageRLogRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(packageRLogForPurgeTest));
    doNothing().when(packageRLogRepository).deleteAll();
    long lastDeletedId = packageTrackerService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 1L);
  }

  @Test
  public void testPurgeWithNoDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);
    packageRLogForPurgeTest.setCreateTs(cal.getTime());

    when(packageRLogRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(packageRLogForPurgeTest));
    doNothing().when(packageRLogRepository).deleteAll();
    long lastDeletedId = packageTrackerService.purge(purgeData, pageReq, 90);
    assertEquals(lastDeletedId, 0L);
  }

  @Test
  public void testPurgeWithFewDataToDeleteBeforeDate() {
    Calendar cal1 = Calendar.getInstance();
    Calendar cal2 = Calendar.getInstance();
    cal1.add(Calendar.HOUR, -60 * 24);
    packageRLogForPurgeTest.setCreateTs(cal1.getTime());
    cal2.add(Calendar.HOUR, -1 * 24);
    packageRLogForPurgeTestNotToBeDeleted.setId(2L);
    packageRLogForPurgeTestNotToBeDeleted.setCreateTs(cal2.getTime());

    when(packageRLogRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(packageRLogForPurgeTest, packageRLogForPurgeTestNotToBeDeleted));
    doNothing().when(packageRLogRepository).deleteAll();
    long lastDeletedId = packageTrackerService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 1L);
  }
}
