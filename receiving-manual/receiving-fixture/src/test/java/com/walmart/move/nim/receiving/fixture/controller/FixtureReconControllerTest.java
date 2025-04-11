package com.walmart.move.nim.receiving.fixture.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.fixture.service.ControlTowerService;
import com.walmart.move.nim.receiving.fixture.service.PalletReceivingService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class FixtureReconControllerTest extends ReceivingControllerTestBase {
  private MockMvc mockMvc;
  private FixtureReconController fixtureReconController;
  @Mock private PalletReceivingService palletReceivingService;
  @Mock private ControlTowerService controlTowerService;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    fixtureReconController = new FixtureReconController();

    ReflectionTestUtils.setField(
        fixtureReconController, "palletReceivingService", palletReceivingService);
    ReflectionTestUtils.setField(
        fixtureReconController, "controlTowerService", controlTowerService);
    this.mockMvc = MockMvcBuilders.standaloneSetup(fixtureReconController).build();
  }

  @AfterMethod
  public void clear() {
    reset(palletReceivingService);
    reset(controlTowerService);
  }

  @Test
  public void testRefreshCTToken() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/recon/fixture/controltower/refreshtoken")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
    verify(controlTowerService, times(1)).refreshToken();
  }

  @Test
  public void testPostContainerToCT() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/recon/fixture/controltower/inventory/a3289900000123")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
    verify(palletReceivingService, times(1)).postInventoryToCT(eq("a3289900000123"));
  }

  @Test
  public void testPublishContainerToInventory() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/recon/fixture/inventory/a3289900000123")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
    verify(palletReceivingService, times(1)).publishToInventory(eq("a3289900000123"));
  }
}
