package com.walmart.move.nim.receiving.fixture.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.fixture.common.CTToken;
import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.fixture.config.FixtureManagedConfig;
import com.walmart.move.nim.receiving.fixture.entity.ControlTowerTracker;
import com.walmart.move.nim.receiving.fixture.mock.data.FixtureMockData;
import com.walmart.move.nim.receiving.fixture.model.CTWarehouseResponse;
import com.walmart.move.nim.receiving.fixture.model.PutAwayInventory;
import com.walmart.move.nim.receiving.fixture.repositories.ControlTowerTrackerRepository;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ControlTowerServiceTest extends ReceivingTestBase {

  public static final String HTTPS_QA_CONTROLTOWER_TECH = "https://qa.controltower.tech";
  @InjectMocks private ControlTowerService controlTowerService;
  @InjectMocks private CTToken ctToken;
  @Mock private FixtureManagedConfig fixtureManagedConfig;
  @Mock private RestConnector simpleRestConnector;
  @Autowired private ControlTowerTrackerRepository controlTowerTrackerRepository;
  private String secretKey = "AtlasReceivingKe";

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(controlTowerService, "secretKey", secretKey);
    ReflectionTestUtils.setField(
        controlTowerService, "controlTowerTrackerRepository", controlTowerTrackerRepository);
  }

  @AfterMethod
  public void tearDown() {
    reset(fixtureManagedConfig);
    reset(simpleRestConnector);
    controlTowerTrackerRepository.deleteAll();
  }

  @Test(
      priority = 1,
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Unable to decrypt.")
  public void testPutAwayInventoryDecyrptionException() {
    when(fixtureManagedConfig.getCtBaseUrl()).thenReturn(HTTPS_QA_CONTROLTOWER_TECH);
    when(fixtureManagedConfig.getCtUserName()).thenReturn("userName");
    when(fixtureManagedConfig.getCtPassword()).thenReturn("HoDTJJ/lBBUsY2pRbQxHzA=");

    when(simpleRestConnector.post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_AUTHENTICATE_URL),
            any(),
            any(HttpHeaders.class),
            same(CTToken.class)))
        .thenReturn(new ResponseEntity<>(getToken(), HttpStatus.OK));
    when(simpleRestConnector.post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_POST_INVENTORY),
            any(),
            any(HttpHeaders.class),
            same(Object.class)))
        .thenReturn(new ResponseEntity<>("7D6335D7F8", HttpStatus.OK));

    PutAwayInventory putAwayInventory =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getCTPutawayInventoryPayload(), PutAwayInventory.class);
    String response =
        controlTowerService.putAwayInventory(Collections.singletonList(putAwayInventory));
    verify(simpleRestConnector, times(0))
        .post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_AUTHENTICATE_URL),
            any(),
            any(HttpHeaders.class),
            same(CTToken.class));
    verify(simpleRestConnector, times(0))
        .post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_POST_INVENTORY),
            any(),
            any(HttpHeaders.class),
            same(Object.class));
  }

  @Test(
      priority = 2,
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Client exception from Control Tower..*")
  public void testPutAwayInventoryAuthException() {
    when(fixtureManagedConfig.getCtBaseUrl()).thenReturn(HTTPS_QA_CONTROLTOWER_TECH);
    when(fixtureManagedConfig.getCtUserName()).thenReturn("userName");
    when(fixtureManagedConfig.getCtPassword()).thenReturn("HoDTJJ/lBBUsY2pRbQxHzA==");

    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.UNAUTHORIZED.value(),
                "",
                null,
                "".getBytes(),
                Charset.forName("UTF-8")))
        .when(simpleRestConnector)
        .post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_AUTHENTICATE_URL),
            any(),
            any(HttpHeaders.class),
            same(CTToken.class));

    PutAwayInventory putAwayInventory =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getCTPutawayInventoryPayload(), PutAwayInventory.class);
    controlTowerService.putAwayInventory(Collections.singletonList(putAwayInventory));
    verify(simpleRestConnector, times(1))
        .post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_AUTHENTICATE_URL),
            any(),
            any(HttpHeaders.class),
            same(CTToken.class));
    verify(simpleRestConnector, times(0))
        .post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_POST_INVENTORY),
            any(),
            any(HttpHeaders.class),
            same(Object.class));
  }

  @Test(
      priority = 3,
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "We are unable to reach Control Tower")
  public void testPutAwayInventoryAuthCTDown() {
    when(fixtureManagedConfig.getCtBaseUrl()).thenReturn(HTTPS_QA_CONTROLTOWER_TECH);
    when(fixtureManagedConfig.getCtUserName()).thenReturn("userName");
    when(fixtureManagedConfig.getCtPassword()).thenReturn("HoDTJJ/lBBUsY2pRbQxHzA==");

    doThrow(new ResourceAccessException("Error"))
        .when(simpleRestConnector)
        .post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_AUTHENTICATE_URL),
            any(),
            any(HttpHeaders.class),
            same(CTToken.class));

    PutAwayInventory putAwayInventory =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getCTPutawayInventoryPayload(), PutAwayInventory.class);
    controlTowerService.putAwayInventory(Collections.singletonList(putAwayInventory));
  }

  @Test(
      priority = 4,
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "We are unable to reach Control Tower")
  public void testPutAwayInventoryCTDown() {
    when(fixtureManagedConfig.getCtBaseUrl()).thenReturn(HTTPS_QA_CONTROLTOWER_TECH);
    when(fixtureManagedConfig.getCtUserName()).thenReturn("userName");
    when(fixtureManagedConfig.getCtPassword()).thenReturn("HoDTJJ/lBBUsY2pRbQxHzA==");
    when(simpleRestConnector.post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_AUTHENTICATE_URL),
            any(),
            any(HttpHeaders.class),
            same(CTToken.class)))
        .thenReturn(new ResponseEntity<>(getToken(), HttpStatus.OK));

    doThrow(new ResourceAccessException("Error"))
        .when(simpleRestConnector)
        .post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_POST_INVENTORY),
            any(),
            any(HttpHeaders.class),
            same(Object.class));

    PutAwayInventory putAwayInventory =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getCTPutawayInventoryPayload(), PutAwayInventory.class);
    controlTowerService.putAwayInventory(Collections.singletonList(putAwayInventory));
  }

  @Test(
      priority = 5,
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Client exception from Control Tower..*")
  public void testPutAwayInventoryException() {
    when(fixtureManagedConfig.getCtBaseUrl()).thenReturn(HTTPS_QA_CONTROLTOWER_TECH);
    when(fixtureManagedConfig.getCtUserName()).thenReturn("userName");
    when(fixtureManagedConfig.getCtPassword()).thenReturn("HoDTJJ/lBBUsY2pRbQxHzA==");

    when(simpleRestConnector.post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_AUTHENTICATE_URL),
            any(),
            any(HttpHeaders.class),
            same(CTToken.class)))
        .thenReturn(new ResponseEntity<>(getToken(), HttpStatus.OK));
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.UNAUTHORIZED.value(),
                "",
                null,
                "".getBytes(),
                Charset.forName("UTF-8")))
        .when(simpleRestConnector)
        .post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_POST_INVENTORY),
            any(),
            any(HttpHeaders.class),
            same(Object.class));

    PutAwayInventory putAwayInventory =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getCTPutawayInventoryPayload(), PutAwayInventory.class);
    controlTowerService.putAwayInventory(Collections.singletonList(putAwayInventory));
  }

  @Test(priority = 6)
  public void testPutAwayInventory() {
    when(fixtureManagedConfig.getCtBaseUrl()).thenReturn(HTTPS_QA_CONTROLTOWER_TECH);
    when(fixtureManagedConfig.getCtUserName()).thenReturn("userName");
    when(fixtureManagedConfig.getCtPassword()).thenReturn("HoDTJJ/lBBUsY2pRbQxHzA==");

    when(simpleRestConnector.post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_AUTHENTICATE_URL),
            any(),
            any(HttpHeaders.class),
            same(CTToken.class)))
        .thenReturn(new ResponseEntity<>(getToken(), HttpStatus.OK));
    when(simpleRestConnector.post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_POST_INVENTORY),
            any(),
            any(HttpHeaders.class),
            same(Object.class)))
        .thenReturn(new ResponseEntity<>("7D6335D7F8", HttpStatus.OK));

    PutAwayInventory putAwayInventory =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getCTPutawayInventoryPayload(), PutAwayInventory.class);
    String response =
        controlTowerService.putAwayInventory(Collections.singletonList(putAwayInventory));
    assertEquals(response, "7D6335D7F8");
    verify(simpleRestConnector, times(0))
        .post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_AUTHENTICATE_URL),
            any(),
            any(HttpHeaders.class),
            same(CTToken.class));
    verify(simpleRestConnector, times(1))
        .post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_POST_INVENTORY),
            any(),
            any(HttpHeaders.class),
            same(Object.class));
  }

  @Test(priority = 7)
  public void testAsyncPutAwayInventory() {
    when(fixtureManagedConfig.getCtBaseUrl()).thenReturn(HTTPS_QA_CONTROLTOWER_TECH);
    when(fixtureManagedConfig.getCtUserName()).thenReturn("userName");
    when(fixtureManagedConfig.getCtPassword()).thenReturn("HoDTJJ/lBBUsY2pRbQxHzA==");

    when(simpleRestConnector.post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_AUTHENTICATE_URL),
            any(),
            any(HttpHeaders.class),
            same(CTToken.class)))
        .thenReturn(new ResponseEntity<>(getToken(), HttpStatus.OK));
    when(simpleRestConnector.post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_POST_INVENTORY),
            any(),
            any(HttpHeaders.class),
            same(Object.class)))
        .thenReturn(new ResponseEntity<>("7D6335D7F8", HttpStatus.OK));

    PutAwayInventory putAwayInventory =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getCTPutawayInventoryPayload(), PutAwayInventory.class);
    ControlTowerTracker controlTowerTracker =
        controlTowerTrackerRepository.save(
            ControlTowerTracker.builder().id(1L).lpn("LPN 10656 INV 4784").build());

    controlTowerService.putAwayInventory(
        Collections.singletonList(putAwayInventory), controlTowerTracker);
    verify(simpleRestConnector, times(0))
        .post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_AUTHENTICATE_URL),
            any(),
            any(HttpHeaders.class),
            same(CTToken.class));
    verify(simpleRestConnector, times(1))
        .post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_POST_INVENTORY),
            any(),
            any(HttpHeaders.class),
            same(Object.class));

    List<ControlTowerTracker> trackerRepositoryAll = controlTowerTrackerRepository.findAll();
    assertNotNull(trackerRepositoryAll);
    assertEquals(trackerRepositoryAll.size(), 1);
    assertEquals(trackerRepositoryAll.get(0).getAckKey(), "7D6335D7F8");
    assertEquals(trackerRepositoryAll.get(0).getLpn(), "LPN 10656 INV 4784");
    assertEquals(trackerRepositoryAll.get(0).getId(), Long.valueOf(1L));
  }

  @Test(priority = 8)
  public void testPutForTracking() {
    controlTowerService.putForTracking("LPN 10656 INV 4784");
    List<ControlTowerTracker> trackerRepositoryAll = controlTowerTrackerRepository.findAll();
    assertNotNull(trackerRepositoryAll);
    assertEquals(trackerRepositoryAll.size(), 1);
    assertNull(trackerRepositoryAll.get(0).getAckKey());
    assertEquals(trackerRepositoryAll.get(0).getLpn(), "LPN 10656 INV 4784");
    assertEquals(trackerRepositoryAll.get(0).getId(), Long.valueOf(2L));
  }

  @Test(priority = 9)
  public void testGetInventoryStatusException() {
    when(fixtureManagedConfig.getCtBaseUrl()).thenReturn(HTTPS_QA_CONTROLTOWER_TECH);
    when(fixtureManagedConfig.getCtUserName()).thenReturn("userName");
    when(fixtureManagedConfig.getCtPassword()).thenReturn("HoDTJJ/lBBUsY2pRbQxHzA==");

    when(simpleRestConnector.post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_AUTHENTICATE_URL),
            any(),
            any(HttpHeaders.class),
            same(CTToken.class)))
        .thenReturn(new ResponseEntity<>(getToken(), HttpStatus.OK));
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.UNAUTHORIZED.value(),
                "",
                null,
                "".getBytes(),
                Charset.forName("UTF-8")))
        .when(simpleRestConnector)
        .exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            same(CTWarehouseResponse.class));

    CTWarehouseResponse response = controlTowerService.getInventoryStatus("A54801F417");
    assertNull(response);
  }

  @Test(priority = 10)
  public void testGetInventoryStatusCTdown() {
    when(fixtureManagedConfig.getCtBaseUrl()).thenReturn(HTTPS_QA_CONTROLTOWER_TECH);
    when(fixtureManagedConfig.getCtUserName()).thenReturn("userName");
    when(fixtureManagedConfig.getCtPassword()).thenReturn("HoDTJJ/lBBUsY2pRbQxHzA==");

    when(simpleRestConnector.post(
            eq(HTTPS_QA_CONTROLTOWER_TECH + FixtureConstants.CT_AUTHENTICATE_URL),
            any(),
            any(HttpHeaders.class),
            same(CTToken.class)))
        .thenReturn(new ResponseEntity<>(getToken(), HttpStatus.OK));
    doThrow(new ResourceAccessException("Some error."))
        .when(simpleRestConnector)
        .exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            same(CTWarehouseResponse.class));

    CTWarehouseResponse response = controlTowerService.getInventoryStatus("A54801F417");
    assertNull(response);
  }

  @Test(priority = 11)
  public void testGetInventoryStatus() {
    when(fixtureManagedConfig.getCtBaseUrl()).thenReturn(HTTPS_QA_CONTROLTOWER_TECH);
    when(fixtureManagedConfig.getCtUserName()).thenReturn("userName");
    when(fixtureManagedConfig.getCtPassword()).thenReturn("HoDTJJ/lBBUsY2pRbQxHzA==");

    when(simpleRestConnector.post(anyString(), any(), any(HttpHeaders.class), same(CTToken.class)))
        .thenReturn(new ResponseEntity<>(getToken(), HttpStatus.OK));
    when(simpleRestConnector.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            same(CTWarehouseResponse.class)))
        .thenReturn(
            new ResponseEntity<>(
                CTWarehouseResponse.builder().status("Success").build(), HttpStatus.OK));

    CTWarehouseResponse response = controlTowerService.getInventoryStatus("A54801F417");
    assertNotNull(response);
  }

  @Test(priority = 12)
  public void testGetCTEntitiesToValidate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.MINUTE, -10);
    Date time10minAgo = cal.getTime();
    List<ControlTowerTracker> trackerList = new ArrayList<>();
    trackerList.add(
        ControlTowerTracker.builder()
            .lpn("LPN 10656 INV 4784")
            .ackKey("A8D2BE3211")
            .submissionStatus(EventTargetStatus.DELETE)
            .build());
    trackerList.add(
        ControlTowerTracker.builder()
            .lpn("LPN 10656 INV 4785")
            .ackKey("B8D2BE3212")
            .submissionStatus(EventTargetStatus.PENDING)
            .build());
    trackerList.add(
        ControlTowerTracker.builder()
            .lpn("LPN 10656 INV 4786")
            .ackKey("C8D2BE3213")
            .submissionStatus(EventTargetStatus.FAILED)
            .build());
    trackerList.add(
        ControlTowerTracker.builder()
            .lpn("LPN 10656 INV 4787")
            .ackKey("D8D2BE3214")
            .submissionStatus(EventTargetStatus.FAILED)
            .build());
    trackerList.add(
        ControlTowerTracker.builder()
            .lpn("LPN 10656 INV 4788")
            .ackKey("E8D2BE3215")
            .submissionStatus(EventTargetStatus.PENDING)
            .build());
    trackerList.add(
        ControlTowerTracker.builder()
            .lpn("LPN 10656 INV 4789")
            .createTs(time10minAgo)
            .submissionStatus(EventTargetStatus.PENDING)
            .build());
    trackerList.add(
        ControlTowerTracker.builder()
            .lpn("LPN 10656 INV 4790")
            .submissionStatus(EventTargetStatus.PENDING)
            .build());
    controlTowerService.saveManagedObjectsOnly(trackerList);
    List<ControlTowerTracker> trackerRepositoryAll = controlTowerTrackerRepository.findAll();
    assertEquals(trackerRepositoryAll.size(), 7);

    // manipulate data for test
    trackerRepositoryAll.forEach(
        ctTracker -> {
          if (Arrays.asList(
                  "LPN 10656 INV 4784",
                  "LPN 10656 INV 4785",
                  "LPN 10656 INV 4786",
                  "LPN 10656 INV 4787",
                  "LPN 10656 INV 4789")
              .contains(ctTracker.getLpn())) ctTracker.setCreateTs(time10minAgo);
          if (ctTracker.getLpn().equalsIgnoreCase("LPN 10656 INV 4787"))
            ctTracker.setRetriesCount(5);
          if (ctTracker.getLpn().equalsIgnoreCase("LPN 10656 INV 4786"))
            ctTracker.setRetriesCount(2);
        });
    controlTowerTrackerRepository.saveAll(trackerRepositoryAll);

    List<ControlTowerTracker> ctEntitiesToValidate = controlTowerService.getCTEntitiesToValidate();
    assertNotNull(ctEntitiesToValidate);
    assertEquals(ctEntitiesToValidate.size(), 3);
    List<String> expectedSelectedLPNS =
        Arrays.asList("LPN 10656 INV 4785", "LPN 10656 INV 4786", "LPN 10656 INV 4789");
    ctEntitiesToValidate.forEach(
        ctTracker -> assertTrue(expectedSelectedLPNS.contains(ctTracker.getLpn())));
  }

  @Test(priority = 13)
  public void testResetForTrackingLpnDoesNotExists() {
    controlTowerService.resetForTracking("LPN 10656 INV 4784");
    List<ControlTowerTracker> trackerRepositoryAll = controlTowerTrackerRepository.findAll();
    assertNotNull(trackerRepositoryAll);
    assertEquals(trackerRepositoryAll.size(), 1);
    assertNull(trackerRepositoryAll.get(0).getAckKey());
    assertEquals(trackerRepositoryAll.get(0).getLpn(), "LPN 10656 INV 4784");
  }

  @Test(priority = 14)
  public void testResetForTracking() {
    controlTowerTrackerRepository.save(
        ControlTowerTracker.builder()
            .id(1L)
            .lpn("LPN 10656 INV 4784")
            .ackKey("EFGHYUN123")
            .submissionStatus(EventTargetStatus.DELETE)
            .retriesCount(2)
            .build());
    controlTowerService.resetForTracking("LPN 10656 INV 4784");
    List<ControlTowerTracker> trackerRepositoryAll = controlTowerTrackerRepository.findAll();
    assertNotNull(trackerRepositoryAll);
    assertEquals(trackerRepositoryAll.size(), 1);
    assertNull(trackerRepositoryAll.get(0).getAckKey());
    assertEquals(trackerRepositoryAll.get(0).getLpn(), "LPN 10656 INV 4784");
    assertEquals(trackerRepositoryAll.get(0).getSubmissionStatus(), EventTargetStatus.PENDING);
    assertEquals(trackerRepositoryAll.get(0).getRetriesCount(), Integer.valueOf(0));
  }

  private CTToken getToken() {
    CTToken ctToken = new CTToken();
    ctToken.setToken(
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Ik9Ua3lRak01UTBJelJUbEdNMEUyTVRRMU1VTTNOemRFUTBGQ01rTTVOVVpHTmpRNVJqUXhNQSJ9.eyJlbWFpbCI6InJlcHN1cHBvcnRAd2FsbWFydC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiaXNzIjoiaHR0cHM6Ly9jb250cm9sdG93ZXIuYXV0aDAuY29tLyIsInN1YiI6ImF1dGgwfDVkZDMxMGRjNTc4MGEzMGVmMGYwMzYyNSIsImF1ZCI6IlhCcHVqZ29ySUFpMDdTZWtCbFdFaEI3cjNTNjJFemV0IiwiaWF0IjoxNjEwNjkzNTM0LCJleHAiOjE2MTA3Mjk1MzR9.Qk1FxdIiXTUiC2G_-8-nxmSXTiiUHFogjlVLNDqs-U6-adW2WLNSxFmoSQ1X3UMVjyhk2h-E8dYX8IQWu0kIncliPpj-hP2wGgF5S7vb9S2zhMDdgf-UcX5tYKoHvgaV3TOe5EolPeaTMduArUICDMC1Z3ORTrKRQOFdYgRjeiVMKHINHgVILt0QyGek-gduSx_qW3sPq5I2MN2LS8S5xweu4587KAFjiXddlpdVgH1pdxpE_xGt7UFsMhHHlpkGDuSrEt0vLV46izBzl02bflpYPYNxNfTO2dY_j4Tz41dB4VPIeT3sDMNqDDaqzj6n8Uo-sEDBgsI3ZtTyXsa5lA");
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, 10);
    ctToken.setExpires(cal.getTime());
    return ctToken;
  }
}
