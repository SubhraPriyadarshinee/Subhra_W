package com.walmart.move.nim.receiving.acc.service;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertNotNull;

import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.entity.UserLocation;
import com.walmart.move.nim.receiving.acc.model.UserLocationRequest;
import com.walmart.move.nim.receiving.acc.model.acl.label.ACLLabelCount;
import com.walmart.move.nim.receiving.acc.model.acl.notification.DeliveryAndLocationMessage;
import com.walmart.move.nim.receiving.acc.repositories.UserLocationRepo;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.DeliveryDocumentHelper;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.item.rules.RuleSet;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.POLineStatus;
import com.walmart.move.nim.receiving.utils.constants.POStatus;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class UserLocationServiceTest extends ReceivingTestBase {

  @InjectMocks private UserLocationService userLocationService;

  @Mock private ACCManagedConfig accManagedConfig;

  @Mock private AppConfig appConfig;

  @Mock private LocationService locationService;

  @Mock private ACLService aclService;

  @Mock private UpdateUserLocationService updateUserLocationService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private UserLocationRepo userLocationRepo;

  @Mock private LabelDataService labelDataService;

  @Mock private DeliveryLinkService deliveryLinkService;

  private UserLocationRequest userLocationRequest;

  private DeliveryAndLocationMessage deliveryAndLocationMessage;

  @Mock private PreLabelDeliveryService genericPreLabelDeliveryEventProcessor;

  @Mock private GenericLabelGeneratorService genericLabelGeneratorService;

  @Mock private DeliveryEventPersisterService deliveryEventPersisterService;

  @Mock private DeliveryService deliveryService;

  @Mock private RuleSet itemCategoryRuleSet;

  @InjectMocks @Spy private DeliveryDocumentHelper deliveryDocumentHelper;

  private Optional<UserLocation> userLocation;

  private Optional<UserLocation> userLocation1;

  private HttpHeaders headers = MockHttpHeaders.getHeaders();

  private LocationInfo locationResponseForOnline;

  private LocationInfo locationResponseForFloorLineMappedDoor;

  private LocationInfo locationResponseForOffline;

  private LocationInfo locationResponseForFloorline;

  private LocationInfo locationResponseForFloorlineMmr;

  private LocationInfo locationResponseForWorkStation;

  private ACLLabelCount aclLabelCountForRepublish;

  private DeliveryDetails deliveryDetailsSSTK;

  private DeliveryDetails deliveryDetailsCROSSU;

  private LabelData labelData1;

  private LabelData labelData2;

  private String scannedFloorLineLocation;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32818);

    userLocationRequest = new UserLocationRequest();
    userLocationRequest.setDeliveryNumber(1234L);
    userLocationRequest.setLocationId("100");

    userLocation = Optional.of(new UserLocation());
    userLocation.get().setLocationId("100");
    userLocation.get().setUserId("sysadmin.s32987");
    userLocation.get().setCreateTs(new Date());
    userLocation.get().setId(1234L);

    userLocation1 = Optional.of(new UserLocation());
    userLocation1.get().setLocationId("101");
    userLocation1.get().setUserId("sysadmin.s32987");
    userLocation1.get().setCreateTs(new Date());
    userLocation1.get().setId(1234L);

    deliveryAndLocationMessage = new DeliveryAndLocationMessage();
    deliveryAndLocationMessage.setUserId("sysadmin");
    deliveryAndLocationMessage.setLocation("100");
    deliveryAndLocationMessage.setDeliveryNbr("1234");

    aclLabelCountForRepublish = new ACLLabelCount();
    aclLabelCountForRepublish.setItemCount(0);
    aclLabelCountForRepublish.setLabelsCount(0);

    labelData1 =
        LabelData.builder()
            .deliveryNumber(94769060L)
            .purchaseReferenceNumber("3615852071")
            .purchaseReferenceLineNumber(8)
            .isDAConveyable(false)
            .labelType(LabelType.ORDERED)
            .lpnsCount(0)
            .build();

    labelData2 =
        LabelData.builder()
            .deliveryNumber(94769060L)
            .purchaseReferenceNumber("3615852071")
            .purchaseReferenceLineNumber(8)
            .labelType(LabelType.ORDERED)
            .lpnsCount(6)
            .isDAConveyable(true)
            .build();

    scannedFloorLineLocation = "EFLCP14";

    ReflectionTestUtils.setField(
        userLocationService, "deliveryDocumentHelper", deliveryDocumentHelper);
  }

  @AfterMethod
  public void resetMocks() {
    reset(accManagedConfig);
    reset(appConfig);
    reset(locationService);
    reset(aclService);
    reset(updateUserLocationService);
    reset(userLocationRepo);
    reset(deliveryLinkService);
    reset(labelDataService);
    reset(genericPreLabelDeliveryEventProcessor);
    reset(genericLabelGeneratorService);
    reset(deliveryEventPersisterService);
    reset(deliveryService);
    reset(itemCategoryRuleSet);
    reset(tenantSpecificConfigReader);
  }

  @BeforeMethod
  public void fetchDeliveryDetails() {
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetailsSSTK.json")
              .getCanonicalPath();
      deliveryDetailsSSTK =
          JacksonParser.convertJsonToObject(
              new String(Files.readAllBytes(Paths.get(dataPath))), DeliveryDetails.class);
      String dataPath2 =
          new File("../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json")
              .getCanonicalPath();
      deliveryDetailsCROSSU =
          JacksonParser.convertJsonToObject(
              new String(Files.readAllBytes(Paths.get(dataPath2))), DeliveryDetails.class);

      locationResponseForOnline =
          LocationInfo.builder()
              .isOnline(Boolean.TRUE)
              .mappedFloorLine(null)
              .isFloorLine(Boolean.FALSE)
              .build();
      locationResponseForFloorLineMappedDoor =
          LocationInfo.builder()
              .isOnline(Boolean.FALSE)
              .mappedFloorLine("EFLCP14")
              .isFloorLine(Boolean.FALSE)
              .build();
      locationResponseForOffline =
          LocationInfo.builder()
              .isOnline(Boolean.FALSE)
              .mappedFloorLine(null)
              .isFloorLine(Boolean.FALSE)
              .mappedParentAclLocation(null)
              .build();
      locationResponseForFloorline =
          LocationInfo.builder()
              .isOnline(Boolean.FALSE)
              .mappedFloorLine("EFLCP14")
              .isFloorLine(Boolean.TRUE)
              .isMultiManifestLocation(Boolean.FALSE)
              .build();
      locationResponseForFloorlineMmr =
          LocationInfo.builder()
              .isOnline(Boolean.FALSE)
              .mappedFloorLine(null)
              .isFloorLine(Boolean.TRUE)
              .isMultiManifestLocation(Boolean.TRUE)
              .build();
      locationResponseForWorkStation =
          LocationInfo.builder()
              .isOnline(Boolean.FALSE)
              .mappedFloorLine(null)
              .isFloorLine(Boolean.FALSE)
              .mappedParentAclLocation("EFLCP15")
              .build();

      when(tenantSpecificConfigReader.isFeatureFlagEnabled(
              eq(ACCConstants.PREGEN_FALLBACK_ENABLED)))
          .thenReturn(Boolean.TRUE);
      when(tenantSpecificConfigReader.isFeatureFlagEnabled(
              eq(ACCConstants.ENABLE_REPUBLISH_FALLBACK_CHECK)))
          .thenReturn(Boolean.TRUE);
      when(tenantSpecificConfigReader.isFeatureFlagEnabled(eq(ACCConstants.ENABLE_MULTI_MANIFEST)))
          .thenReturn(Boolean.FALSE);
      when(tenantSpecificConfigReader.getConfiguredInstance(
              anyString(),
              eq(ReceivingConstants.DELIVERY_LINK_SERVICE),
              eq(DeliveryLinkService.class)))
          .thenReturn(deliveryLinkService);
      when(tenantSpecificConfigReader.getConfiguredInstance(
              anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), eq(DeliveryService.class)))
          .thenReturn(deliveryService);
      when(tenantSpecificConfigReader.getConfiguredInstance(
              anyString(),
              eq(ReceivingConstants.LABEL_GENERATOR_SERVICE),
              eq(GenericLabelGeneratorService.class)))
          .thenReturn(genericLabelGeneratorService);
      when(tenantSpecificConfigReader.isFeatureFlagEnabled(
              ACCConstants.ENABLE_DELIVERY_LINE_LEVEL_FBQ_CHECK))
          .thenReturn(Boolean.TRUE);

    } catch (IOException e) {
      assert (false);
    }
  }

  @Test
  public void testLocationIsAclEnabled() throws ReceivingException {
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(updateUserLocationService.updateUserLocation(
            "sysadmin.s32987", "100", locationResponseForOnline, headers))
        .thenReturn(userLocation.get());
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForOnline);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    assertTrue(locationResponse.getIsOnline());
    assertNull(locationResponse.getMappedFloorLine());
  }

  @Test
  public void testLocationIsFloorLineMappedWithAclEnabled() throws ReceivingException {
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(updateUserLocationService.updateUserLocation(
            "sysadmin.s32987", "101", locationResponseForFloorLineMappedDoor, headers))
        .thenReturn(userLocation1.get());
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    assertFalse(locationResponse.getIsOnline());
    assertNotNull(locationResponse.getMappedFloorLine());
    assertTrue(locationResponse.getMappedFloorLine().equals("EFLCP14"));
  }

  @Test
  public void testGetUsersAtLocation() {
    when(userLocationRepo.findAllByLocationId(userLocation.get().getLocationId()))
        .thenReturn(Arrays.asList(userLocation.get()));
    List<String> usersAtLocation =
        userLocationService.getUsersAtLocation(userLocation.get().getLocationId(), false);
    assertEquals(usersAtLocation.size(), 1);
    assertEquals(usersAtLocation.get(0), userLocation.get().getUserId());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "No user found at door.*")
  public void testGetUsersAtLocationNoUsers() {
    when(userLocationRepo.findAllByLocationId(userLocation.get().getLocationId())).thenReturn(null);
    userLocationService.getUsersAtLocation(userLocation.get().getLocationId(), false);
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "No user found at door.*")
  public void testGetUsersAtLocationUserIdMissing() {
    UserLocation user = new UserLocation();
    user.setLocationId("100");
    user.setCreateTs(new Date());
    when(userLocationRepo.findAllByLocationId(user.getLocationId())).thenReturn(null);
    userLocationService.getUsersAtLocation(user.getLocationId(), false);
  }

  @Test
  public void testGetUsersAtLocation_MultiManifest() {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(eq(ACCConstants.ENABLE_MULTI_MANIFEST)))
        .thenReturn(Boolean.TRUE);
    when(userLocationRepo.findAllByLocationIdOrParentLocationId(
            userLocation.get().getLocationId(), userLocation.get().getLocationId()))
        .thenReturn(Arrays.asList(userLocation.get()));
    List<String> usersAtLocation =
        userLocationService.getUsersAtLocation(userLocation.get().getLocationId(), false);
    assertEquals(usersAtLocation.size(), 1);
    assertEquals(usersAtLocation.get(0), userLocation.get().getUserId());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "No user found at door.*")
  public void testGetUsersAtLocationNoUsers_MultiManifest() {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(eq(ACCConstants.ENABLE_MULTI_MANIFEST)))
        .thenReturn(Boolean.TRUE);
    when(userLocationRepo.findAllByLocationIdOrParentLocationId(
            userLocation.get().getLocationId(), userLocation.get().getLocationId()))
        .thenReturn(null);
    userLocationService.getUsersAtLocation(userLocation.get().getLocationId(), false);
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "No user found at door.*")
  public void testGetUsersAtLocationUserIdMissing_MultiManifest() {
    UserLocation user = new UserLocation();
    user.setLocationId("100");
    user.setCreateTs(new Date());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(eq(ACCConstants.ENABLE_MULTI_MANIFEST)))
        .thenReturn(Boolean.TRUE);
    when(userLocationRepo.findAllByLocationIdOrParentLocationId(
            user.getLocationId(), user.getLocationId()))
        .thenReturn(null);
    userLocationService.getUsersAtLocation(user.getLocationId(), false);
  }

  @Test
  public void testFallbackTriggeredForOnlineDoor() throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any(), any())).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(0);
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForOnline);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    verify(aclService, times(0)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testFallbackTriggeredForMappedFloorLine() throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any(), any())).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(0);
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    verify(aclService, times(0)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testFallbackTriggeredForChannelFlip_SSTK_to_CROSSU() throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any(), any())).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.singletonList(labelData1));
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    verify(aclService, times(0)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testFallbackTriggeredForChannelFlip_CROSSU_to_SSTK() throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any(), any())).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsSSTK);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.singletonList(labelData2));
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    verify(aclService, times(0)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testFallbackTriggeredForPOQuantityChanged() throws ReceivingException {
    deliveryDetailsCROSSU
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setExpectedQty(8);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any(), any())).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.singletonList(labelData2));
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    verify(aclService, times(0)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testFallbackTriggeredForPOAdded() throws ReceivingException {
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDetailsCROSSU.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setPurchaseReferenceLineNumber(10);
    deliveryDetailsCROSSU
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .add(deliveryDocumentLine);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any(), any())).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.singletonList(labelData2));
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    verify(aclService, times(0)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .processDeliveryEvent(any(MessageData.class));
    deliveryDocumentLine.setPurchaseReferenceLineNumber(8);
  }

  @Test
  public void testFallbackNotTriggeredForNoChannelFlip_SSTK() throws ReceivingException {
    when(aclService.fetchLabelsFromACL(anyLong())).thenReturn(aclLabelCountForRepublish);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any(), any())).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsSSTK);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.singletonList(labelData1));
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    verify(aclService, times(1)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(0))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testFallbackNotTriggeredForCancelledPo() throws ReceivingException {
    deliveryDetailsCROSSU
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(POStatus.CNCL.name());
    when(aclService.fetchLabelsFromACL(anyLong())).thenReturn(aclLabelCountForRepublish);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any(), any())).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.singletonList(labelData1));
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    verify(aclService, times(1)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(0))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testFallbackNotTriggeredForCancelledPoLine() throws ReceivingException {
    deliveryDetailsCROSSU
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.name());
    when(aclService.fetchLabelsFromACL(anyLong())).thenReturn(aclLabelCountForRepublish);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any(), any())).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.emptyList());
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    verify(aclService, times(1)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(0))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testFallbackNotTriggeredForRejectedPoLine() throws ReceivingException {
    deliveryDetailsCROSSU
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getOperationalInfo()
        .setState(POLineStatus.REJECTED.name());
    when(aclService.fetchLabelsFromACL(anyLong())).thenReturn(aclLabelCountForRepublish);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any(), any())).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.emptyList());
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    verify(aclService, times(1)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(0))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testFallbackNotTriggeredForNoChannelFlip_CROSSU() throws ReceivingException {
    when(aclService.fetchLabelsFromACL(anyLong())).thenReturn(aclLabelCountForRepublish);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any(), any())).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.singletonList(labelData2));
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    verify(aclService, times(1)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(0))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testFallbackTriggeredForExistingInProgressEvent() throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any(), any())).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(1);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsSSTK);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsSSTK);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.singletonList(labelData1));
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    verify(aclService, times(0)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(1))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testRepublishFallbackCheck() throws ReceivingException {
    when(aclService.fetchLabelsFromACL(anyLong())).thenReturn(null);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ACCConstants.PREGEN_FALLBACK_ENABLED), any()))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ACCConstants.ENABLE_REPUBLISH_FALLBACK_CHECK), any()))
        .thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForOnline);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.singletonList(labelData2));
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    verify(aclService, times(1)).fetchLabelsFromACL(anyLong());
    verify(genericLabelGeneratorService, times(1))
        .publishACLLabelDataForDelivery(anyLong(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testDeliveryLineLevelFBQCheckFeatureOnWithInvalidLine() throws ReceivingException {
    when(aclService.fetchLabelsFromACL(anyLong())).thenReturn(null);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ACCConstants.PREGEN_FALLBACK_ENABLED), any()))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ACCConstants.ENABLE_REPUBLISH_FALLBACK_CHECK), any()))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ACCConstants.ENABLE_DELIVERY_LINE_LEVEL_FBQ_CHECK))
        .thenReturn(Boolean.TRUE);

    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForOnline);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);

    // Setting as import PO
    deliveryDetailsCROSSU.getDeliveryDocuments().get(0).setImportInd(Boolean.TRUE);

    // Setting FBQ 0 for delivery Line
    deliveryDetailsCROSSU
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setFreightBillQty(0);

    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.singletonList(labelData2));

    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testDeliveryLineLevelFBQCheckFeatureOnWithInvalidLineNullFBQ()
      throws ReceivingException {
    when(aclService.fetchLabelsFromACL(anyLong())).thenReturn(null);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ACCConstants.PREGEN_FALLBACK_ENABLED), any()))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ACCConstants.ENABLE_REPUBLISH_FALLBACK_CHECK), any()))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ACCConstants.ENABLE_DELIVERY_LINE_LEVEL_FBQ_CHECK))
        .thenReturn(Boolean.TRUE);

    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForOnline);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);

    // Setting FBQ 0 for delivery Line
    deliveryDetailsCROSSU
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setFreightBillQty(null);

    // Setting as import PO
    deliveryDetailsCROSSU.getDeliveryDocuments().get(0).setImportInd(Boolean.TRUE);

    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.singletonList(labelData2));

    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
  }

  @Test
  public void testDeliveryLineLevelFBQCheckFeatureOnWithInvalidLineNullFBQ_NonImports()
      throws ReceivingException {
    when(aclService.fetchLabelsFromACL(anyLong())).thenReturn(null);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ACCConstants.PREGEN_FALLBACK_ENABLED), any()))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ACCConstants.ENABLE_REPUBLISH_FALLBACK_CHECK), any()))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ACCConstants.ENABLE_DELIVERY_LINE_LEVEL_FBQ_CHECK))
        .thenReturn(Boolean.TRUE);

    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForOnline);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);

    // Setting FBQ 0 for delivery Line
    deliveryDetailsCROSSU
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setFreightBillQty(null);

    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.singletonList(labelData2));

    try {
      LocationInfo locationResponse =
          userLocationService.getLocationInfo(userLocationRequest, headers);
    } catch (ReceivingBadDataException rbde) {
      fail("No exception is supposed to be thrown");
    }
  }

  @Test
  public void testFullyDaConForNoChannelFlip_SSTK() throws ReceivingException {
    when(aclService.fetchLabelsFromACL(anyLong())).thenReturn(aclLabelCountForRepublish);
    when(accManagedConfig.isFullyDaConEnabled()).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsSSTK);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.singletonList(labelData1));
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    when(itemCategoryRuleSet.validateRuleSet(
            any(com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine.class)))
        .thenReturn(Boolean.FALSE);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    assertNull(locationResponse.getIsFullyDaCon());
    verify(aclService, times(0)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(0))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testFullyDaConForCancelledPo() throws ReceivingException {
    deliveryDetailsCROSSU
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(POStatus.CNCL.name());
    when(aclService.fetchLabelsFromACL(anyLong())).thenReturn(aclLabelCountForRepublish);
    when(accManagedConfig.isFullyDaConEnabled()).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.singletonList(labelData1));
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    when(itemCategoryRuleSet.validateRuleSet(
            any(com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine.class)))
        .thenReturn(Boolean.FALSE);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    assertTrue(locationResponse.getIsFullyDaCon());
    verify(aclService, times(0)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(0))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testFullyDaConForCancelledPoLine() throws ReceivingException {
    deliveryDetailsCROSSU
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.name());
    when(aclService.fetchLabelsFromACL(anyLong())).thenReturn(aclLabelCountForRepublish);
    when(accManagedConfig.isFullyDaConEnabled()).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.emptyList());
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    when(itemCategoryRuleSet.validateRuleSet(
            any(com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine.class)))
        .thenReturn(Boolean.FALSE);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    assertTrue(locationResponse.getIsFullyDaCon());
    verify(aclService, times(0)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(0))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testFullyDaConForRejectedPoLine() throws ReceivingException {
    deliveryDetailsCROSSU
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getOperationalInfo()
        .setState(POLineStatus.REJECTED.name());
    when(aclService.fetchLabelsFromACL(anyLong())).thenReturn(aclLabelCountForRepublish);
    when(accManagedConfig.isFullyDaConEnabled()).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.emptyList());
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    when(itemCategoryRuleSet.validateRuleSet(
            any(com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine.class)))
        .thenReturn(Boolean.FALSE);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    assertTrue(locationResponse.getIsFullyDaCon());
    verify(aclService, times(0)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(0))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testFullyDaConFalseForRegulatedItems() throws ReceivingException {
    deliveryDetailsCROSSU
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(Collections.emptyList());
    when(aclService.fetchLabelsFromACL(anyLong())).thenReturn(aclLabelCountForRepublish);
    when(accManagedConfig.isFullyDaConEnabled()).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.emptyList());
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    when(itemCategoryRuleSet.validateRuleSet(
            any(com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine.class)))
        .thenReturn(Boolean.TRUE);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    assertNull(locationResponse.getIsFullyDaCon());
    verify(aclService, times(0)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(0))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testFullyDaConForNoChannelFlip_CROSSU() throws ReceivingException {
    when(aclService.fetchLabelsFromACL(anyLong())).thenReturn(aclLabelCountForRepublish);
    when(accManagedConfig.isFullyDaConEnabled()).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(10);
    when(deliveryEventPersisterService.getCountOfInProgressAndPendingEvents(anyLong()))
        .thenReturn(0);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    when(labelDataService.getLabelDataByDeliveryNumber(anyLong()))
        .thenReturn(Collections.singletonList(labelData2));
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    assertTrue(locationResponse.getIsFullyDaCon());
    verify(aclService, times(0)).fetchLabelsFromACL(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(0))
        .processDeliveryEvent(any(MessageData.class));
  }

  @Test
  public void testCreateUserLocationMappingForFloorLine_HappyFlow_FloorLine() {
    when(locationService.getDoorInfo(scannedFloorLineLocation, false))
        .thenReturn(locationResponseForFloorline);
    when(updateUserLocationService.updateUserLocation(
            "sysadmin.s32987", scannedFloorLineLocation, locationResponseForFloorline, headers))
        .thenReturn(userLocation.get());
    userLocationService.createUserLocationMappingForFloorLine(scannedFloorLineLocation, headers);
    verify(locationService, times(1)).getDoorInfo(scannedFloorLineLocation, false);
    verify(updateUserLocationService, times(1))
        .updateUserLocation(
            "sysadmin.s32987", scannedFloorLineLocation, locationResponseForFloorline, headers);
  }

  @Test
  public void testCreateUserLocationMappingForFloorLine_HappyFlow_OnlineDoor() {
    when(locationService.getDoorInfo(scannedFloorLineLocation, false))
        .thenReturn(locationResponseForOnline);
    when(updateUserLocationService.updateUserLocation(
            "sysadmin.s32987", scannedFloorLineLocation, locationResponseForOnline, headers))
        .thenReturn(userLocation.get());
    userLocationService.createUserLocationMappingForFloorLine(scannedFloorLineLocation, headers);
    verify(locationService, times(1)).getDoorInfo(scannedFloorLineLocation, false);
    verify(updateUserLocationService, times(1))
        .updateUserLocation(
            "sysadmin.s32987", scannedFloorLineLocation, locationResponseForOnline, headers);
  }

  @Test
  public void testCreateUserLocationMappingForFloorLine_HappyFlow_WorkStation() {
    when(locationService.getDoorInfo(scannedFloorLineLocation, false))
        .thenReturn(locationResponseForWorkStation);
    when(updateUserLocationService.updateUserLocation(
            "sysadmin.s32987", scannedFloorLineLocation, locationResponseForWorkStation, headers))
        .thenReturn(userLocation.get());
    userLocationService.createUserLocationMappingForFloorLine(scannedFloorLineLocation, headers);
    verify(locationService, times(1)).getDoorInfo(scannedFloorLineLocation, false);
    verify(updateUserLocationService, times(1))
        .updateUserLocation(
            "sysadmin.s32987", scannedFloorLineLocation, locationResponseForWorkStation, headers);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "The scanned location is not valid. Please scan a valid floor line ACL.")
  public void testCreateUserLocationMappingForFloorLine_LocationInvalid_OfflineDoor() {
    when(locationService.getDoorInfo(scannedFloorLineLocation, false))
        .thenReturn(locationResponseForOffline);
    when(updateUserLocationService.updateUserLocation(
            "sysadmin.s32987", scannedFloorLineLocation, locationResponseForOffline, headers))
        .thenReturn(userLocation.get());
    userLocationService.createUserLocationMappingForFloorLine(scannedFloorLineLocation, headers);
    verify(locationService, times(1)).getDoorInfo(scannedFloorLineLocation, false);
    verify(updateUserLocationService, times(0))
        .updateUserLocation(
            "sysadmin.s32987", scannedFloorLineLocation, locationResponseForOffline, headers);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "The scanned location is not valid. Please scan a valid floor line ACL.")
  public void testCreateUserLocationMappingForFloorLine_LocationInvalid_FloorLineMappedDoor() {
    when(locationService.getDoorInfo(scannedFloorLineLocation, false))
        .thenReturn(locationResponseForFloorLineMappedDoor);
    when(updateUserLocationService.updateUserLocation(
            "sysadmin.s32987",
            scannedFloorLineLocation,
            locationResponseForFloorLineMappedDoor,
            headers))
        .thenReturn(userLocation.get());
    userLocationService.createUserLocationMappingForFloorLine(scannedFloorLineLocation, headers);
    verify(locationService, times(1)).getDoorInfo(scannedFloorLineLocation, false);
    verify(updateUserLocationService, times(0))
        .updateUserLocation(
            "sysadmin.s32987",
            scannedFloorLineLocation,
            locationResponseForFloorLineMappedDoor,
            headers);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "The scanned location is a multi manifest location. Please scan a valid work location.")
  public void
      testCreateUserLocationMappingForFloorLine_LocationInvalid_FloorLineWithMultiManifest() {
    when(locationService.getDoorInfo(scannedFloorLineLocation, false))
        .thenReturn(locationResponseForFloorlineMmr);
    when(updateUserLocationService.updateUserLocation(
            "sysadmin.s32987", scannedFloorLineLocation, locationResponseForFloorlineMmr, headers))
        .thenReturn(userLocation.get());
    userLocationService.createUserLocationMappingForFloorLine(scannedFloorLineLocation, headers);
    verify(locationService, times(1)).getDoorInfo(scannedFloorLineLocation, false);
    verify(updateUserLocationService, times(0))
        .updateUserLocation(
            "sysadmin.s32987", scannedFloorLineLocation, locationResponseForFloorlineMmr, headers);
  }

  @Test
  public void testGetLocationInfo_ExceptionLaneReceiving() throws ReceivingException {
    userLocationRequest.setIsOverflowReceiving(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any(), any())).thenReturn(Boolean.TRUE);
    when(accManagedConfig.getFallbackGenerationTimeout()).thenReturn(10);
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(0);
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForOnline);
    when(deliveryService.getDeliveryDetails(anyString(), anyLong()))
        .thenReturn(deliveryDetailsCROSSU);
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    assertNotNull(locationResponse);
    verify(locationService, times(1)).getDoorInfo(userLocationRequest.getLocationId(), false);
    verify(aclService, times(0)).fetchLabelsFromACL(anyLong());
    verify(labelDataService, times(0)).countByDeliveryNumber(anyLong());
    verify(deliveryLinkService, times(0)).updateDeliveryLink(any(), any());
    verify(updateUserLocationService, times(0))
        .updateUserLocation(anyString(), anyString(), any(), any(HttpHeaders.class));
    verify(genericPreLabelDeliveryEventProcessor, times(0))
        .processDeliveryEvent(any(MessageData.class));
    userLocationRequest.setIsOverflowReceiving(null);
  }

  @Test
  public void test_deleteByLocation() {
    String mockLocationId = "a123";
    doNothing()
        .when(userLocationRepo)
        .deleteByLocationId(eq(StringUtils.upperCase(mockLocationId)));
    userLocationService.deleteByLocation(mockLocationId);
    verify(userLocationRepo, times(1))
        .deleteByLocationId(eq(StringUtils.upperCase(mockLocationId)));
  }

  @Test
  public void testGetLocationInfo_DeliveryHasPassThroughFreights() throws ReceivingException {
    DeliveryDetails deliveryDetails = deliveryDetailsCROSSU;
    deliveryDetails
        .getDeliveryDocuments()
        .forEach(
            deliveryDocument ->
                deliveryDocument
                    .getDeliveryDocumentLines()
                    .forEach(
                        deliveryDocumentLine ->
                            deliveryDocumentLine.setPurchaseRefType(
                                PurchaseReferenceType.POCON.name())));
    when(locationService.getDoorInfo(userLocationRequest.getLocationId(), false))
        .thenReturn(locationResponseForOnline);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any(), any())).thenReturn(Boolean.TRUE);
    when(updateUserLocationService.updateUserLocation(
            "sysadmin.s32987", scannedFloorLineLocation, locationResponseForFloorline, headers))
        .thenReturn(userLocation.get());
    when(deliveryService.getDeliveryDetails(anyString(), anyLong())).thenReturn(deliveryDetails);
    userLocationService.getLocationInfo(userLocationRequest, headers);
    verify(labelDataService, times(1)).countByDeliveryNumber(anyLong());
    verify(genericPreLabelDeliveryEventProcessor, times(0))
        .processDeliveryEvent(any(MessageData.class));
    verify(accManagedConfig, times(0)).getFallbackGenerationTimeout();
  }
}
