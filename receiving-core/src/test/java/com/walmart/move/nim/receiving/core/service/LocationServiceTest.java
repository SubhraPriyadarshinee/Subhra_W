package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaWftLocationMessagePublisher;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.core.model.LocationSummary;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LocationServiceTest extends ReceivingTestBase {

  @Mock private RetryableRestConnector retryableRestConnector;

  @Mock private AppConfig appConfig;

  @Mock private JmsPublisher jmsPublisher;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private KafkaWftLocationMessagePublisher kafkaWftLocationMessagePublisher;

  @InjectMocks private LocationService locationService;

  private final String locationBaseUrl = "https://gls-atlas-gls-location.walmart.com";

  @BeforeClass
  public void initMocks() {
    TenantContext.setFacilityNum(32987);
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        retryableRestConnector,
        appConfig,
        tenantSpecificConfigReader,
        jmsPublisher,
        kafkaWftLocationMessagePublisher);
  }

  @Test
  public void testgetBulkLocationInfo() {

    JsonObject response = new JsonObject();
    response.addProperty("totalCount", 1);
    response.addProperty("successCount", 1);
    Gson locresp = new Gson();
    String result = locresp.toJson(response);

    when(appConfig.getLocationBaseuRL()).thenReturn(locationBaseUrl);
    doReturn(new ResponseEntity<>(result, HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), anyCollection(), any(HttpHeaders.class), eq(String.class));
    response =
        locationService.getBulkLocationInfo(
            Arrays.asList("test1", "test2"), MockHttpHeaders.getHeaders());
    assertNotNull(response);
  }

  @Test
  public void testgetBulkLocationInfoNullResponse() {

    when(appConfig.getLocationBaseuRL()).thenReturn(locationBaseUrl);
    doReturn(new ResponseEntity<>("", HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), anyCollection(), any(HttpHeaders.class), eq(String.class));
    assertNull(
        locationService.getBulkLocationInfo(
            Arrays.asList("test1", "test2"), MockHttpHeaders.getHeaders()));
  }

  @Test
  public void testOnlineDoorSuccessful() {
    doReturn(new ResponseEntity<>(getLocationSuccessfulOnlineDoorResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    LocationInfo locationInfo = locationService.getDoorInfo("1004", false);
    assertTrue(locationInfo.getIsOnline(), "Location was supposed to be online");
    assertEquals(locationInfo.getMappedPbylArea(), "PTR001");
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test()
  public void testLocationEmptyResponseScenario() {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_EMPTY_LOCATION_RESPONSE_ERROR)))
        .thenReturn(false);
    doReturn(new ResponseEntity<>(getLocationEmptyResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    LocationInfo locationInfo = locationService.getDoorInfo("1005", false);
    assertFalse(locationInfo.getIsOnline(), "Location was supposed to be offline");
    assertNull(locationInfo.getMappedPbylArea());
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testLocationEmptyResponseScenario_EmptyResponseErrorEnabled() {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_EMPTY_LOCATION_RESPONSE_ERROR)))
        .thenReturn(true);
    doReturn(new ResponseEntity<>(getLocationEmptyResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    try {
      LocationInfo locationInfo = locationService.getDoorInfo("1005", false);
    } catch (ReceivingDataNotFoundException exc) {
      assertEquals(exc.getErrorCode(), ExceptionCodes.LOCATION_RESP_IS_EMPTY);
      verify(retryableRestConnector, times(1))
          .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    }
  }

  @Test
  public void testOfflineDoorWithoutFlMappingSuccessful() {
    doReturn(
            new ResponseEntity<>(
                getLocationSuccessfulOfflineWithoutFlMappingDoorResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    LocationInfo locationInfo = locationService.getDoorInfo("1006", false);
    assertFalse(locationInfo.getIsOnline(), "Location was supposed to be offline");
    assertNull(
        locationInfo.getMappedFloorLine(), "Location is not supposed to have a floor line mapping");
    assertEquals(locationInfo.getMappedPbylArea(), "PTR001");
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testOfflineDoorWithFlMappingSuccessful() {
    doReturn(
            new ResponseEntity<>(
                getLocationSuccessfulOfflineWithFlMappingDoorResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    LocationInfo locationInfo = locationService.getDoorInfo("1007", false);
    assertFalse(locationInfo.getIsOnline(), "Location was supposed to be offline");
    assertEquals(
        locationInfo.getMappedFloorLine(), "EFLCP08", "Location has wrong floor line mapping");
    assertEquals(locationInfo.getMappedPbylArea(), "PTR001");
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testFloorLineLocationSuccessful() {
    doReturn(new ResponseEntity<>(getLocationSuccessfulFloorLineResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    LocationInfo locationInfo = locationService.getDoorInfo("EFLCP08", false);
    assertFalse(locationInfo.getIsOnline(), "Location was supposed to be offline");
    assertEquals(
        locationInfo.getMappedFloorLine(), "EFLCP08", "Location has wrong floor line mapping");
    assertEquals(locationInfo.getMappedPbylArea(), "PTR001");
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testMultiManifestFloorLineLocationSuccessful() {
    doReturn(
            new ResponseEntity<>(
                getLocationSuccessfulMultiManifestFloorLineResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    LocationInfo locationInfo = locationService.getDoorInfo("EFLCP08", false);
    assertFalse(locationInfo.getIsOnline(), "Location was supposed to be offline");
    assertEquals(
        locationInfo.getMappedFloorLine(), "EFLCP08", "Location has wrong floor line mapping");
    assertEquals(locationInfo.getMappedPbylArea(), "PTR001");
    assertTrue(
        locationInfo.getIsMultiManifestLocation(),
        "This is supposed to be a multi manifest location");
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testWorkStationLocationSuccessful() {
    doReturn(
            new ResponseEntity<>(getLocationSuccessfulWorkStationLocationResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    LocationInfo locationInfo = locationService.getDoorInfo("WS001", false);
    assertFalse(locationInfo.getIsOnline(), "Location was supposed to be offline");
    assertEquals(locationInfo.getMappedFloorLine(), "WS001");
    assertEquals(locationInfo.getMappedPbylArea(), "PTR001");
    assertEquals(locationInfo.getMappedParentAclLocation(), "EFLCP09");
    assertFalse(
        locationInfo.getIsMultiManifestLocation(),
        "This is not supposed to be a multi manifest location");
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Error while searching for location = 1008")
  public void testLocationServiceDown() {
    doThrow(new ResourceAccessException("Error"))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    locationService.getDoorInfo("1008", false);
    fail("Service down exception is supposed to be thrown");

    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Location 1009 not found")
  public void testLocationNotFound() {
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.NOT_FOUND.value(),
                "",
                null,
                "".getBytes(),
                Charset.forName("UTF-8")))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    locationService.getDoorInfo("1009", false);
    fail("Service down exception is supposed to be thrown");

    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testIsOnlineOrHasFloorLine_OnlineDoor() {
    doReturn(new ResponseEntity<>(getLocationSuccessfulOnlineDoorResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    assertTrue(locationService.isOnlineOrHasFloorLine("1004"));
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testIsOnlineOrHasFloorLine_LocationEmptyResponseScenario() {
    doReturn(new ResponseEntity<>(getLocationEmptyResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    assertFalse(locationService.isOnlineOrHasFloorLine("1005"));
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testIsOnlineOrHasFloorLine_OfflineDoorWithoutFlMapping() {
    doReturn(
            new ResponseEntity<>(
                getLocationSuccessfulOfflineWithoutFlMappingDoorResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    assertFalse(locationService.isOnlineOrHasFloorLine("1006"));
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testIsOnlineOrHasFloorLine_OfflineDoorWithFlMappingSuccessful() {
    doReturn(
            new ResponseEntity<>(
                getLocationSuccessfulOfflineWithFlMappingDoorResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    assertTrue(locationService.isOnlineOrHasFloorLine("1007"));
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testPbylLocationSuccessful() {
    doReturn(new ResponseEntity<>(getLocationSuccessfulPbylLocationResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    doReturn("DEFAULTFL")
        .when(tenantSpecificConfigReader)
        .getDCSpecificMoveFloorLineDestinationForNonConDockTag(anyInt());
    LocationInfo locationInfo = locationService.getLocationInfoForPbylDockTag("PTR001");
    assertEquals(locationInfo.getMappedFloorLine(), "EFLCP08");
    assertFalse(locationInfo.getIsOnline());
    assertFalse(locationInfo.getIsFloorLine());
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    verify(tenantSpecificConfigReader, times(0))
        .getDCSpecificMoveFloorLineDestinationForNonConDockTag(anyInt());
  }

  @Test
  public void testPbylLocation_MappedFloorLineNotFound() {
    doReturn(
            new ResponseEntity<>(
                getLocationSuccessfulPbylLocationResponseWithoutMappedFloorLine(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    doReturn("DEFAULTFL")
        .when(tenantSpecificConfigReader)
        .getDCSpecificMoveFloorLineDestinationForNonConDockTag(anyInt());
    LocationInfo locationInfo = locationService.getLocationInfoForPbylDockTag("PTR001");
    assertEquals(locationInfo.getMappedFloorLine(), "DEFAULTFL");
    assertFalse(locationInfo.getIsOnline());
    assertFalse(locationInfo.getIsFloorLine());
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    verify(tenantSpecificConfigReader, times(1))
        .getDCSpecificMoveFloorLineDestinationForNonConDockTag(anyInt());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testPbylLocation_LocationNotFound() {
    doReturn(new ResponseEntity<>(getLocationEmptyResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    doReturn("DEFAULTFL")
        .when(tenantSpecificConfigReader)
        .getDCSpecificMoveFloorLineDestinationForNonConDockTag(anyInt());
    locationService.getLocationInfoForPbylDockTag("PTR001");
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    verify(tenantSpecificConfigReader, times(0))
        .getDCSpecificMoveFloorLineDestinationForNonConDockTag(anyInt());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testPbylLocation_WithoutPbylDoor() {
    doReturn(new ResponseEntity<>(getPbylLocationResponseWithoutPbylDoorTag(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    doReturn("DEFAULTFL")
        .when(tenantSpecificConfigReader)
        .getDCSpecificMoveFloorLineDestinationForNonConDockTag(anyInt());
    LocationInfo locationInfo = locationService.getLocationInfoForPbylDockTag("PTR001");
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    verify(tenantSpecificConfigReader, times(0))
        .getDCSpecificMoveFloorLineDestinationForNonConDockTag(anyInt());
  }

  @Test
  public void testValidLocationResponseForRdc_publishToWFTEnabled() throws IOException {
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    doReturn(new ResponseEntity<>(getValidLocationResponseForRdc(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    doNothing()
        .when(jmsPublisher)
        .publish(any(String.class), any(ReceivingJMSEvent.class), any(Boolean.class));
    LocationInfo locationInfo =
        locationService.getLocationInfoByIdentifier("FID11", MockHttpHeaders.getHeaders());
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    verify(jmsPublisher, times(1))
        .publish(any(String.class), any(ReceivingJMSEvent.class), any(Boolean.class));
    assertTrue(Objects.nonNull(locationInfo));
    assertNotNull(locationInfo.getLocationId());
    assertNotNull(locationInfo.getLocationType());
    assertNotNull(locationInfo.getSccCode());
    assertNotNull(locationInfo.getLocationName());
    assertNotNull(locationInfo.getLocationSubType());
  }

  @Test
  public void testValidLocationResponseForRdc_publishToWFTEnabled_KafkaEnabled()
      throws IOException {
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    doReturn(new ResponseEntity<>(getValidLocationResponseForRdc(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    doNothing()
        .when(jmsPublisher)
        .publish(any(String.class), any(ReceivingJMSEvent.class), any(Boolean.class));
    LocationInfo locationInfo =
        locationService.getLocationInfoByIdentifier("FID11", MockHttpHeaders.getHeaders());
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    verify(kafkaWftLocationMessagePublisher, times(1))
        .publish(any(LocationSummary.class), anyMap());
    verify(jmsPublisher, times(0))
        .publish(any(String.class), any(ReceivingJMSEvent.class), any(Boolean.class));
    assertTrue(Objects.nonNull(locationInfo));
  }

  @Test
  public void testValidLocationResponseForRdc_publishToWFTEnabled_Kafka_notEnabled()
      throws IOException {
    when(appConfig.isWftPublishEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    doReturn(new ResponseEntity<>(getValidLocationResponseForRdc(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    LocationInfo locationInfo =
        locationService.getLocationInfoByIdentifier("FID11", MockHttpHeaders.getHeaders());
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    verify(kafkaWftLocationMessagePublisher, times(0))
        .publish(any(LocationSummary.class), anyMap());
    verify(jmsPublisher, times(1))
        .publish(any(String.class), any(ReceivingJMSEvent.class), any(Boolean.class));
    assertTrue(Objects.nonNull(locationInfo));
  }

  @Test
  public void testValidLocationResponseForRdc_publishToWFTDisabled() throws IOException {
    when(appConfig.isWftPublishEnabled()).thenReturn(false);
    doReturn(new ResponseEntity<>(getValidLocationResponseForRdc(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    LocationInfo locationInfo =
        locationService.getLocationInfoByIdentifier("FID11", MockHttpHeaders.getHeaders());
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    verify(jmsPublisher, times(0))
        .publish(any(String.class), any(ReceivingJMSEvent.class), any(Boolean.class));
    assertTrue(Objects.nonNull(locationInfo));
    assertNotNull(locationInfo.getLocationId());
    assertNotNull(locationInfo.getLocationType());
    assertNotNull(locationInfo.getSccCode());
    assertNotNull(locationInfo.getLocationName());
    assertNotNull(locationInfo.getLocationSubType());
  }

  @Test
  public void testValidLocationResponseForRdc_FlibDoor() throws IOException {
    when(appConfig.isWftPublishEnabled()).thenReturn(false);
    doReturn(new ResponseEntity<>(getValidLocationResponseForRdc(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    LocationInfo locationInfo =
        locationService.getLocationInfoByIdentifier("FID11", MockHttpHeaders.getHeaders());
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    assertTrue(Objects.nonNull(locationInfo));
    assertNotNull(locationInfo.getLocationId());
    assertNotNull(locationInfo.getLocationType());
    assertNotNull(locationInfo.getSccCode());
    assertNotNull(locationInfo.getLocationName());
    assertNotNull(locationInfo.getIsFlibLocation());
    assertNotNull(locationInfo.getLocationSubType());
    assertSame(true, locationInfo.getIsFlibLocation());
  }

  @Test
  public void testValidLocationResponseForRdc_NotAFlibDoor() throws IOException {
    when(appConfig.isWftPublishEnabled()).thenReturn(false);
    doReturn(new ResponseEntity<>(locationResponseForRdcMissingMandatoryFields(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    LocationInfo locationInfo =
        locationService.getLocationInfoByIdentifier("FID11", MockHttpHeaders.getHeaders());
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    assertTrue(Objects.nonNull(locationInfo));
    assertNotNull(locationInfo.getLocationId());
    assertNotNull(locationInfo.getSccCode());
    assertNotNull(locationInfo.getLocationName());
    assertNotNull(locationInfo.getIsFlibLocation());
    assertSame(false, locationInfo.getIsFlibLocation());
  }

  @Test(
      expectedExceptions = ReceivingConflictException.class,
      expectedExceptionsMessageRegExp =
          "The locationId or locationType or locationName or sccCode is missing in location api response for locationId: FID11")
  public void testLocationResponseMissingMandatoryFields() throws IOException {
    doReturn(new ResponseEntity<>(locationResponseForRdcMissingMandatoryFields(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));

    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());

    locationService.getLocationInfoByIdentifier("FID11", MockHttpHeaders.getHeaders());
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test(
      expectedExceptions = ReceivingConflictException.class,
      expectedExceptionsMessageRegExp =
          "The locationId or locationType or locationName or sccCode is missing in location api response for locationId: FID11")
  public void testLocationResponseMissingMandatoryFields_PropertiesPresentScCodeMissing()
      throws IOException {
    doReturn(
            new ResponseEntity<>(
                locationResponseForRdcMissingMandatoryFields_PropertiesPresentScCodeMissing(),
                HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));

    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());

    locationService.getLocationInfoByIdentifier("FID11", MockHttpHeaders.getHeaders());
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testLocationResponseMissingMandatoryFields_ResponseValidation_Disabled()
      throws IOException {
    doReturn(new ResponseEntity<>(locationResponseForRdcMissingMandatoryFields(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));

    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    final LocationInfo locationInfo =
        locationService.getLocationInfoByIdentifier("FID11", MockHttpHeaders.getHeaders());
    Assert.notNull(locationInfo, "should not be null or thrown exception ");
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "The scanned location FID11 is not valid. Please scan a valid location.")
  public void testEmptyLocationResponseForRdc() {
    doReturn(new ResponseEntity<>(getLocationEmptyResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    locationService.getLocationInfoByIdentifier("FID11", MockHttpHeaders.getHeaders());
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  private String getLocationSuccessfulOnlineDoorResponse() {
    try {
      String dataPath =
          new File(
                  "../receiving-test/src/main/resources/"
                      + "json/LocationLocationsSearchOnline.json")
              .getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  private String getLocationSuccessfulOfflineWithoutFlMappingDoorResponse() {

    try {
      String dataPath =
          new File(
                  "../receiving-test/src/main/resources/"
                      + "json/LocationLocationsSearchOfflineWithoutFloorLineMapping.json")
              .getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  private String getLocationSearchMappedDecantStation() {

    try {
      String dataPath =
          new File(
                  "../receiving-test/src/main/resources/"
                      + "json/locationSearchMappedDecantStation.json")
              .getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  private String getLocationSuccessfulPbylLocationResponse() {

    try {
      String dataPath =
          new File(
                  "../receiving-test/src/main/resources/"
                      + "json/LocationLocationsSearchPbylLocation.json")
              .getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  private String getPbylLocationResponseWithoutPbylDoorTag() {

    try {
      String dataPath =
          new File(
                  "../receiving-test/src/main/resources/"
                      + "json/LocationLocationsSearchPbylLocationWithoutPbylDoor.json")
              .getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  private String getLocationSuccessfulPbylLocationResponseWithoutMappedFloorLine() {

    try {
      String dataPath =
          new File(
                  "../receiving-test/src/main/resources/"
                      + "json/LocationLocationsSearchPbylLocationWithoutMappedFloorLine.json")
              .getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  private String getLocationSuccessfulOfflineWithFlMappingDoorResponse() {

    try {
      String dataPath =
          new File(
                  "../receiving-test/src/main/resources/"
                      + "json/LocationLocationsSearchOfflineWithFloorLineMapping.json")
              .getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  private String getLocationSuccessfulFloorLineResponse() {

    try {
      String dataPath =
          new File(
                  "../receiving-test/src/main/resources/"
                      + "json/LocationLocationsSearchOfflineWithFloorLineMapping.json")
              .getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  private String getLocationSuccessfulMultiManifestFloorLineResponse() {

    try {
      String dataPath =
          new File(
                  "../receiving-test/src/main/resources/"
                      + "json/LocationLocationsSearchMultiManifestFloorLineLocation.json")
              .getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  private String getLocationSuccessfulWorkStationLocationResponse() {

    try {
      String dataPath =
          new File(
                  "../receiving-test/src/main/resources/"
                      + "json/LocationLocationsSearchWorkStationLocation.json")
              .getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  private String getLocationEmptyResponse() {

    try {
      String dataPath =
          new File(
                  "../receiving-test/src/main/resources/"
                      + "json/LocationLocationsSearchEmpty.json")
              .getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  private String getValidLocationResponseForRdc() throws IOException {
    String dataPath =
        new File("../receiving-test/src/main/resources/json/ValidLocationResponseForRdc.json")
            .getCanonicalPath();
    return new String(Files.readAllBytes(Paths.get(dataPath)));
  }

  private String locationResponseForRdcMissingMandatoryFields() throws IOException {
    String dataPath =
        new File(
                "../receiving-test/src/main/resources/json/LocationResponseMissingMandatoryFields.json")
            .getCanonicalPath();
    return new String(Files.readAllBytes(Paths.get(dataPath)));
  }

  private String locationResponseForRdcMissingMandatoryFields_PropertiesPresentScCodeMissing()
      throws IOException {
    String dataPath =
        new File(
                "../receiving-test/src/main/resources/json/LocationResponseMissingMandatoryFieldsWithPropertiesScCodeMissing.json")
            .getCanonicalPath();
    return new String(Files.readAllBytes(Paths.get(dataPath)));
  }

  @Test
  public void testUserLocationDoorInfo() {
    doReturn(new ResponseEntity<>(getLocationSuccessfulOnlineDoorResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    LocationInfo locationInfo = locationService.getDoorInfo("1009", true);
    assertTrue(!locationInfo.getLocationName().isEmpty());
    assertTrue(!locationInfo.getLocationType().isEmpty());
    assertTrue(!locationInfo.getLocationId().isEmpty());
  }

  @Test
  public void testLocationForMappedDecantStationSuccessful() {
    doReturn(new ResponseEntity<>(getLocationSearchMappedDecantStation(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    LocationInfo locationInfo =
        locationService.getLocationInfoByIdentifier("1006", MockHttpHeaders.getHeaders());
    assertEquals(locationInfo.getMappedDecantStation(), "PTR002");
    assertEquals(locationInfo.getLocationType(), ReceivingConstants.LOCATION_TYPE_SLOT);
    assertEquals(
        locationInfo.getLocationSubType(), ReceivingConstants.LOCATION_SUBTYPE_INDUCT_SLOT);
    assertEquals(locationInfo.getLocationId(), "6050");
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testLocationInfo_mech_swisslog_Successful() {
    final String mechanizedlocationJson =
        getFileAsString(
            "../receiving-test/src/main/resources/json/locationSearchMechanized_swisslog.json");
    doReturn(new ResponseEntity<>(mechanizedlocationJson, HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    LocationInfo locationInfo = locationService.getLocationInfo("glblstor");
    assertEquals(locationInfo.getLocationId(), "1808738");
    assertFalse(locationInfo.getIsPrimeSlot());
    assertEquals(locationInfo.getAutomationType(), "swisslog");
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testLocationInfo_mech_dematic_Successful() {
    final String mechanizedlocationJson =
        getFileAsString(
            "../receiving-test/src/main/resources/json/locationSearchMechanized_dematic.json");
    doReturn(new ResponseEntity<>(mechanizedlocationJson, HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    LocationInfo locationInfo = locationService.getLocationInfo("glblstor");
    assertEquals(locationInfo.getLocationId(), "1808738");
    assertTrue(locationInfo.getIsPrimeSlot());
    assertEquals(locationInfo.getAutomationType(), "dematic");
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testLocationInfo_mech_failed_LocationsNotFound() {
    doReturn(new ResponseEntity<>(getLocationEmptyResponse(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    locationService.getLocationInfo("dematic");
    verify(retryableRestConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  public static String getValidLocationResponseForAutoFc() {

    try {
      String dataPath =
          new File("../receiving-test/src/main/resources/json/ValidLocationResponseForEG.json")
              .getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  @Test
  public void testValidLocationResponseForEG() {
    doReturn(new ResponseEntity<>(getValidLocationResponseForAutoFc(), HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    JsonArray locationArray = locationService.getLocationInfoAsJsonArray("200");
    assertTrue(Objects.nonNull(locationArray));
    assertTrue(Objects.nonNull(locationArray.get(0)));
    JsonObject locationObject = locationArray.get(0).getAsJsonObject();
    assertNotNull(locationObject);
    assertNotNull(locationObject.get(ReceivingConstants.PROPERTIES));
  }
}
