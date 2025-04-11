package com.walmart.move.nim.receiving.acc.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.model.UserLocationRequest;
import com.walmart.move.nim.receiving.acc.service.UserLocationService;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class UserLocationControllerTest extends ReceivingControllerTestBase {

  private HttpHeaders headers = MockHttpHeaders.getHeaders();

  private MockMvc mockMvc;

  @InjectMocks private UserLocationController userLocationController;

  @Mock private UserLocationService userLocationService;

  private UserLocationRequest userLocationRequest;

  @BeforeMethod
  public void setup() {
    userLocationRequest.setDeliveryNumber(1234L);
    userLocationRequest.setLocationId("100");
  }

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    userLocationRequest = new UserLocationRequest();
    mockMvc = MockMvcBuilders.standaloneSetup(userLocationController).build();
  }

  @Test
  public void testHappyPathForOnlineDoorResponse() throws Exception {
    when(userLocationService.getLocationInfo(any(), any())).thenReturn(getOnlineDoorResponse());

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/location-users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new Gson().toJson(userLocationRequest))
                .headers(headers))
        .andExpect(status().isOk());
  }

  @Test
  public void testHappyPathFlowForFloorLineLocationResponse() throws Exception {
    when(userLocationService.getLocationInfo(userLocationRequest, headers))
        .thenReturn(getFloorLineResponse());

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/location-users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new Gson().toJson(userLocationRequest))
                .headers(headers))
        .andExpect(status().isOk());
  }

  private LocationInfo getOnlineDoorResponse() {
    LocationInfo locationInfo =
        LocationInfo.builder().isOnline(Boolean.TRUE).mappedFloorLine(null).build();

    return locationInfo;
  }

  private LocationInfo getFloorLineResponse() {
    LocationInfo locationInfo =
        LocationInfo.builder().isOnline(Boolean.FALSE).mappedFloorLine("EFLCP14").build();

    return locationInfo;
  }
}
