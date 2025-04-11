package com.walmart.move.nim.receiving.acc.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.core.service.LocationService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class PbylControllerTest extends ReceivingTestBase {

  private MockMvc mockMvc;

  @InjectMocks private PbylController pbylController;
  @Mock private LocationService locationService;

  private LocationInfo locationResponseForPbylLocation;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    mockMvc = MockMvcBuilders.standaloneSetup(pbylController).build();

    locationResponseForPbylLocation =
        LocationInfo.builder()
            .isOnline(Boolean.FALSE)
            .mappedFloorLine("EFLCP14")
            .mappedPbylArea(null)
            .isFloorLine(Boolean.TRUE)
            .build();
  }

  @AfterMethod
  public void resetMocks() {
    reset(locationService);
  }

  @Test
  public void testFloorLineSummary() throws Exception {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    when(locationService.getLocationInfoForPbylDockTag(anyString()))
        .thenReturn(locationResponseForPbylLocation);

    LocationInfo pbylScanLocationResponse =
        JacksonParser.convertJsonToObject(
            mockMvc
                .perform(MockMvcRequestBuilders.get("/pbyl/ptr001").headers(headers))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            LocationInfo.class);

    assertEquals(pbylScanLocationResponse, locationResponseForPbylLocation);
    verify(locationService, times(1)).getLocationInfoForPbylDockTag("ptr001");
  }
}
