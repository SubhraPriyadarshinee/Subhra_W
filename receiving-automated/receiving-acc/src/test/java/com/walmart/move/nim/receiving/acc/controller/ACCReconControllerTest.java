package com.walmart.move.nim.receiving.acc.controller;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ONEOPS_ENVIRONMENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.walmart.move.nim.receiving.acc.service.GenericLabelGeneratorService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ACCReconControllerTest extends ReceivingTestBase {

  private static final String DELIVERY_NUMBER = "12345678";
  private MockMvc mockMvc;
  private ACCReconController accReconController;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private GenericLabelGeneratorService genericLabelGeneratorService;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    accReconController = new ACCReconController();
    ReflectionTestUtils.setField(
        accReconController, "tenantSpecificConfigReader", tenantSpecificConfigReader);

    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(genericLabelGeneratorService);
    TenantContext.setFacilityNum(32835);
    System.setProperty(ONEOPS_ENVIRONMENT, "dev");
    this.mockMvc = MockMvcBuilders.standaloneSetup(accReconController).build();
  }

  @Test
  public void testGetAclLogs() throws Exception {
    doNothing().when(genericLabelGeneratorService).publishACLLabelDataForDelivery(anyLong(), any());

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/automated/recon/publish-labels/" + DELIVERY_NUMBER)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders))
        .andExpect(status().isOk());
  }
}
