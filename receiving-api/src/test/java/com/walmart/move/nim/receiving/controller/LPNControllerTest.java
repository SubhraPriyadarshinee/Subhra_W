package com.walmart.move.nim.receiving.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LPNControllerTest extends ReceivingControllerTestBase {

  private MockMvc mockMvc;
  LPNController lpnController;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DockTagService dockTagService;

  @BeforeClass
  public void init() {
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");

    MockitoAnnotations.initMocks(this);
    lpnController = new LPNController();

    ReflectionTestUtils.setField(
        lpnController, "tenantSpecificConfigReader", tenantSpecificConfigReader);
    ReflectionTestUtils.setField(lpnController, "dockTagService", dockTagService);

    this.mockMvc = MockMvcBuilders.standaloneSetup(lpnController).build();
  }

  @AfterMethod
  public void clear() {
    reset(dockTagService);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void testReceiveUniversalTagSuccess() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(dockTagService);

    HttpHeaders headers = MockHttpHeaders.getHeaders();

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/lpn/a100000000/receive?doorNumber=TEST").headers(headers))
        .andExpect(status().isOk());
    verify(dockTagService, times(1)).receiveUniversalTag(eq("a100000000"), eq("TEST"), eq(headers));
  }
}
