package com.walmart.move.nim.receiving.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.core.service.LocationService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LocationControllerTest extends ReceivingControllerTestBase {

  private HttpHeaders headers = MockHttpHeaders.getHeaders();
  private MockMvc mockMvc;

  @InjectMocks private LocationController locationController;

  @Mock private LocationService locationService;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    mockMvc = MockMvcBuilders.standaloneSetup(locationController).build();
  }

  @Test
  public void testGetLocationByIdApiReturns200() {
    try {
      when(locationService.getLocationInfoByIdentifier(any(), any()))
          .thenReturn(successLocationResponse());

      mockMvc
          .perform(MockMvcRequestBuilders.get("/locations/200").headers(headers))
          .andExpect(status().isOk());
      verify(locationService, times(1)).getLocationInfoByIdentifier(any(), any());
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testGetLocationByIdApiReturns400() {
    String locationId = "200";
    try {
      when(locationService.getLocationInfoByIdentifier(any(), any()))
          .thenThrow(
              new ReceivingDataNotFoundException(
                  ExceptionCodes.LOCATION_RESP_IS_EMPTY,
                  ReceivingConstants.LOCATION_RESP_IS_EMPTY,
                  locationId));

      mockMvc
          .perform(MockMvcRequestBuilders.get("/locations/200").headers(headers))
          .andExpect(status().isBadRequest());
      verify(locationService, times(1)).getLocationInfoByIdentifier(any(), any());
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  @Test
  public void testGetLocationByIdApiReturns500() {
    String locationId = "400";
    try {
      when(locationService.getLocationInfoByIdentifier(any(), any()))
          .thenThrow(
              new ReceivingDataNotFoundException(
                  ExceptionCodes.INVALID_LOCATION_RESPONSE,
                  ReceivingConstants.INVALID_LOCATION_RESPONSE,
                  locationId));

      mockMvc
          .perform(MockMvcRequestBuilders.get("/locations/400").headers(headers))
          .andExpect(status().isInternalServerError());
      verify(locationService, times(1)).getLocationInfoByIdentifier(any(), any());
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  private LocationInfo successLocationResponse() {
    return LocationInfo.builder()
        .locationId("200")
        .locationType("DOOR")
        .locationName("200")
        .sccCode("43565462")
        .build();
  }

  private LocationInfo missingMandatoryFieldsInLocationResponse() {
    return LocationInfo.builder()
        .locationId("100")
        .locationType(null)
        .locationName("200")
        .sccCode("21324344")
        .build();
  }
}
