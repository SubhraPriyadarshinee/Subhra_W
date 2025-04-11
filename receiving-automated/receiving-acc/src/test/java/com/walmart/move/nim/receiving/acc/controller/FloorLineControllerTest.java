package com.walmart.move.nim.receiving.acc.controller;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.walmart.move.nim.receiving.acc.service.UserLocationService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class FloorLineControllerTest extends ReceivingTestBase {

  private MockMvc mockMvc;

  @InjectMocks private FloorLineController floorLineController;
  @Mock private UserLocationService userLocationService;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    mockMvc = MockMvcBuilders.standaloneSetup(floorLineController).build();
  }

  @Test
  public void testFloorLineSummary() throws Exception {
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    mockMvc
        .perform(MockMvcRequestBuilders.get("/floor-line/99999").headers(headers))
        .andExpect(status().isOk());

    verify(userLocationService, times(1)).createUserLocationMappingForFloorLine("99999", headers);
  }
}
