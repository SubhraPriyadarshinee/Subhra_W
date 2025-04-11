package com.walmart.move.nim.receiving.acc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.acc.constants.LocationType;
import com.walmart.move.nim.receiving.acc.entity.UserLocation;
import com.walmart.move.nim.receiving.acc.repositories.UserLocationRepo;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Date;
import java.util.Optional;
import org.mockito.AdditionalAnswers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class UpdateUserLocationServiceTest extends ReceivingTestBase {

  @InjectMocks private UpdateUserLocationService updateUserLocationService;

  @Mock private UserLocationRepo userLocationRepo;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  private Optional<UserLocation> userLocation;

  private Optional<UserLocation> userLocation1;
  private LocationInfo locationResponseForOnline;
  private LocationInfo locationResponseForFloorLine;
  private LocationInfo locationResponeForFloorLineLocation;
  private LocationInfo locationResponseForWorkStation;
  private LocationInfo locationResponseForOffline;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32888);

    userLocation = Optional.of(new UserLocation());
    userLocation.get().setLocationId("100");
    userLocation.get().setUserId("sysadmin.s32987");
    userLocation.get().setCreateTs(new Date());
    userLocation.get().setId(1234L);
    userLocation.get().setLocationType(LocationType.ONLINE);

    userLocation1 = Optional.of(new UserLocation());
    userLocation1.get().setLocationId("101");
    userLocation1.get().setUserId("sysadmin.s32987");
    userLocation1.get().setCreateTs(new Date());
    userLocation1.get().setId(1234L);
    userLocation.get().setLocationType(LocationType.ONLINE);

    locationResponseForOnline =
        LocationInfo.builder().isOnline(Boolean.TRUE).mappedFloorLine(null).build();
    locationResponseForFloorLine =
        LocationInfo.builder().isOnline(Boolean.FALSE).mappedFloorLine("EFLCP14").build();
    locationResponeForFloorLineLocation =
        LocationInfo.builder().isOnline(Boolean.FALSE).isFloorLine(Boolean.TRUE).build();
    locationResponseForWorkStation =
        LocationInfo.builder()
            .isOnline(Boolean.FALSE)
            .mappedFloorLine(null)
            .mappedParentAclLocation("MFLCP01")
            .build();
    locationResponseForOffline =
        LocationInfo.builder().isOnline(Boolean.FALSE).mappedFloorLine(null).build();
  }

  @Test
  public void testSuccessForAddingNewUserInfoInDB() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(userLocationRepo.findByUserId("sysadmin.s32987")).thenReturn(Optional.empty());
    when(userLocationRepo.save(any(UserLocation.class)))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    UserLocation userLocationResponse =
        updateUserLocationService.updateUserLocation(
            "sysadmin.s32987", "100", locationResponseForOnline, httpHeaders);
    assertNotNull(userLocationResponse);
    assertTrue(userLocationResponse.getUserId().equals("sysadmin.s32987"));
    assertTrue(userLocationResponse.getLocationId().equals("100"));
    assertEquals(userLocationResponse.getLocationType(), LocationType.ONLINE);
  }

  @Test
  public void testSuccessForAddingNewUserInfoInDBForOfflineDoor() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(userLocationRepo.findByUserId("sysadmin.s32987")).thenReturn(Optional.empty());
    when(userLocationRepo.save(any(UserLocation.class)))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    UserLocation userLocationResponse =
        updateUserLocationService.updateUserLocation(
            "sysadmin.s32987", "100", locationResponseForOffline, httpHeaders);
    assertNotNull(userLocationResponse);
    assertTrue(userLocationResponse.getUserId().equals("sysadmin.s32987"));
    assertTrue(userLocationResponse.getLocationId().equals("100"));
    assertEquals(userLocationResponse.getLocationType(), LocationType.OFFLINE);
  }

  @Test
  public void testSuccessForUpdatingExistingUserInfoInDB() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(userLocationRepo.findByUserId("sysadmin.s32987")).thenReturn(userLocation);
    when(userLocationRepo.save(any(UserLocation.class)))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    UserLocation userLocationResponse =
        updateUserLocationService.updateUserLocation(
            "sysadmin.s32987", "101", locationResponseForOnline, httpHeaders);
    assertNotNull(userLocationResponse);
    assertTrue(userLocationResponse.getUserId().equals("sysadmin.s32987"));
    assertTrue(userLocationResponse.getLocationId().equals("101"));
    assertEquals(userLocationResponse.getLocationType(), LocationType.ONLINE);
    assertNotNull(userLocationResponse.getCreateTs());
  }

  @Test
  public void testUserAlreadyExistsInThatLocationTriggersUpdateInDB() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(userLocationRepo.findByUserId("sysadmin.s32987")).thenReturn(userLocation);
    when(userLocationRepo.save(any(UserLocation.class))).thenReturn(userLocation.get());
    UserLocation userLocationResponse =
        updateUserLocationService.updateUserLocation(
            "sysadmin.s32987", "101", locationResponseForOnline, httpHeaders);
    assertNotNull(userLocationResponse);
    assertTrue(userLocationResponse.getUserId().equals("sysadmin.s32987"));
    assertTrue(userLocationResponse.getLocationId().equals("101"));
    assertNotNull(userLocationResponse.getCreateTs());
  }

  @Test
  public void testSuccessForUpdatingExistingUserInfoInDBFlrLine() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(userLocationRepo.findByUserId("sysadmin.s32987")).thenReturn(userLocation);
    when(userLocationRepo.save(any(UserLocation.class)))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    UserLocation userLocationResponse =
        updateUserLocationService.updateUserLocation(
            "sysadmin.s32987", "101", locationResponseForFloorLine, httpHeaders);
    assertNotNull(userLocationResponse);
    assertTrue(userLocationResponse.getUserId().equals("sysadmin.s32987"));
    assertTrue(userLocationResponse.getLocationId().equals("101"));
    assertEquals(userLocationResponse.getLocationType(), LocationType.FLR_LINE);
    assertNotNull(userLocationResponse.getCreateTs());
  }

  @Test
  public void testSuccessForUpdatingExistingUserInfoInDBFlrLine_KotlinEnabled_isFloorlineTrue() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(IS_KOTLIN_CLIENT, TRUE_STRING);
    when(userLocationRepo.findByUserId("sysadmin.s32987")).thenReturn(userLocation);
    when(userLocationRepo.save(any(UserLocation.class)))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    UserLocation userLocationResponse =
        updateUserLocationService.updateUserLocation(
            "sysadmin.s32987", "101", locationResponeForFloorLineLocation, httpHeaders);
    assertNotNull(userLocationResponse);
    assertTrue(userLocationResponse.getUserId().equals("sysadmin.s32987"));
    assertTrue(userLocationResponse.getLocationId().equals("101"));
    assertEquals(userLocationResponse.getLocationType(), LocationType.FLR_LINE);
    assertNotNull(userLocationResponse.getCreateTs());
  }

  @Test
  public void testSuccessForUpdatingExistingUserInfoInDBWorkStation() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(userLocationRepo.findByUserId("sysadmin.s32987")).thenReturn(userLocation);
    when(userLocationRepo.save(any(UserLocation.class)))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    UserLocation userLocationResponse =
        updateUserLocationService.updateUserLocation(
            "sysadmin.s32987", "101", locationResponseForWorkStation, httpHeaders);
    assertNotNull(userLocationResponse);
    assertTrue(userLocationResponse.getUserId().equals("sysadmin.s32987"));
    assertTrue(userLocationResponse.getLocationId().equals("101"));
    assertEquals(userLocationResponse.getLocationType(), LocationType.OFFLINE);
    assertNotNull(userLocationResponse.getCreateTs());
    assertEquals("MFLCP01", userLocationResponse.getParentLocationId());
  }
}
