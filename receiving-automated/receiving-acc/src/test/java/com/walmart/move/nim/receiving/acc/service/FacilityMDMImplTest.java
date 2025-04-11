package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class FacilityMDMImplTest extends ReceivingTestBase {
  @Mock private RestConnector restConnector;

  @Mock private ACCManagedConfig accManagedConfig;

  @InjectMocks private FacilityMDMImpl facilityMDMImpl;

  private String facilityMDMResponse;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn("facilityMDMURL").when(accManagedConfig).getFacilityMDMBaseUrl();
    doReturn("/facility").when(accManagedConfig).getFacilityMDMDCAlignmentPath();
    doReturn("API-KEY").when(accManagedConfig).getFacilityMDMApiKey();
    doReturn(25).when(accManagedConfig).getFacilityMDMApiCallBatchSize();
    doReturn("WH").when(accManagedConfig).getFacilityMDMDCAlignmentSubCategory();
  }

  @AfterMethod
  public void resetMocks() {
    reset(restConnector);
  }

  @Test
  public void testGetStoreToDCMapping_success() {
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/FacilityMDMSuccess.json")
              .getCanonicalPath();
      facilityMDMResponse = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    doReturn(new ResponseEntity<>(facilityMDMResponse, HttpStatus.OK))
        .when(restConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    Map<String, Integer> storeToDCMap =
        facilityMDMImpl.getStoreToDCMapping(
            Arrays.asList("US10", "US92"), MockHttpHeaders.getHeaders());
    assertEquals(storeToDCMap.get("10"), Integer.valueOf(6094));
    verify(restConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testGetStoreToDCMapping_failure() {
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/FacilityMDMFailure.json")
              .getCanonicalPath();
      facilityMDMResponse = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    doReturn(new ResponseEntity<>(facilityMDMResponse, HttpStatus.INTERNAL_SERVER_ERROR))
        .when(restConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    Map<String, Integer> storeToDCMap =
        facilityMDMImpl.getStoreToDCMapping(
            Arrays.asList("US10", "US92"), MockHttpHeaders.getHeaders());
    assertNull(storeToDCMap.get("10"));
    verify(restConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testGetStoreToDCMapping_invalidSubCategoryCode() {
    try {
      String dataPath =
          new File(
                  "../../receiving-test/src/main/resources/json/FacilityMDMSuccessSubCatNotWH.json")
              .getCanonicalPath();
      facilityMDMResponse = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    doReturn(new ResponseEntity<>(facilityMDMResponse, HttpStatus.OK))
        .when(restConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    Map<String, Integer> storeToDCMap =
        facilityMDMImpl.getStoreToDCMapping(
            Arrays.asList("US10", "US92"), MockHttpHeaders.getHeaders());
    assertNull(storeToDCMap.get("10"));
    verify(restConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testGetStoreToDCMapping_multipleAlignment() {
    try {
      String dataPath =
          new File(
                  "../../receiving-test/src/main/resources/json/FacilityMDMSuccessMultipleAlignment.json")
              .getCanonicalPath();
      facilityMDMResponse = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    doReturn(new ResponseEntity<>(facilityMDMResponse, HttpStatus.OK))
        .when(restConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    Map<String, Integer> storeToDCMap =
        facilityMDMImpl.getStoreToDCMapping(
            Arrays.asList("US10", "US92"), MockHttpHeaders.getHeaders());
    assertEquals(storeToDCMap.get("10"), Integer.valueOf(6069));
    verify(restConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testGetStoreToDCMapping_multipleBatches() {
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/FacilityMDMSuccess.json")
              .getCanonicalPath();
      facilityMDMResponse = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    doReturn(new ResponseEntity<>(facilityMDMResponse, HttpStatus.OK))
        .when(restConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    Map<String, Integer> storeToDCMap =
        facilityMDMImpl.getStoreToDCMapping(
            Arrays.asList(
                "US10", "US11", "US12", "US13", "US14", "US15", "US16", "US17", "US18", "US19",
                "US20", "US21", "US22", "US23", "US24", "US25", "US26", "US27", "US28", "US29",
                "US30", "US31", "US32", "US33", "US34", "US35", "US36", "US37", "US38", "US39"),
            MockHttpHeaders.getHeaders());
    assertEquals(storeToDCMap.get("10"), Integer.valueOf(6094));
    verify(restConnector, times(2))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testGetStoreToDCMapping_mdmCallThrowsException() {
    doThrow(new ResourceAccessException("Not able to access resource"))
        .when(restConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    Map<String, Integer> storeToDCMap =
        facilityMDMImpl.getStoreToDCMapping(
            Collections.singletonList("US10"), MockHttpHeaders.getHeaders());
    assertNull(storeToDCMap.get("10"));
    verify(restConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }

  @Test
  public void testGetStoreToDCMapping_okWithInvalidData() {
    doReturn(new ResponseEntity<>("invalid data e.g. not a json", HttpStatus.OK))
        .when(restConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    Map<String, Integer> storeToDCMap =
        facilityMDMImpl.getStoreToDCMapping(
            Collections.singletonList("US10"), MockHttpHeaders.getHeaders());
    assertNull(storeToDCMap.get("10"));
    verify(restConnector, times(1))
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
  }
}
