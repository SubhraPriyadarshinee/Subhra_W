package com.walmart.move.nim.receiving.acc.controller;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ONEOPS_ENVIRONMENT;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.AssertJUnit.assertEquals;

import com.walmart.move.nim.receiving.acc.mock.data.MockLabelData;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.service.LabelDataLpnService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LabelDataControllerTest extends ReceivingTestBase {

  @InjectMocks private LabelDataController labelDataController;

  @InjectMocks private RestResponseExceptionHandler restResponseExceptionHandler;

  @Mock private LabelDataLpnService labelDataLpnService;

  private MockMvc mockMvc;

  private String lpn;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    labelDataController = new LabelDataController();
    restResponseExceptionHandler = new RestResponseExceptionHandler();
    ReflectionTestUtils.setField(labelDataController, "labelDataLpnService", labelDataLpnService);
    System.setProperty(ONEOPS_ENVIRONMENT, "dev");
    this.mockMvc =
        MockMvcBuilders.standaloneSetup(labelDataController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
    lpn = "c32987000000000000000001";
  }

  @AfterMethod
  public void resetMocks() {
    reset(labelDataLpnService);
  }

  @Test
  public void testGetLabelDataByLpn() throws Exception {
    when(labelDataLpnService.findLabelDataByLpn(lpn))
        .thenReturn(Optional.of(MockLabelData.getMockLabelData()));
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/label-data/" + lpn)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders))
        .andExpect(status().isOk());
  }

  @Test
  public void testGetLabelDataByLpn_LabelDataNotFound() {
    when(labelDataLpnService.findLabelDataByLpn(lpn)).thenReturn(Optional.empty());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    try {
      mockMvc.perform(
          MockMvcRequestBuilders.get("/label-data/" + lpn)
              .contentType(MediaType.APPLICATION_JSON)
              .headers(httpHeaders));
    } catch (Exception e) {
      assertEquals(
          e.getCause().getMessage(),
          String.format(ExceptionDescriptionConstants.LABEL_DATA_NOT_FOUND_ERROR_MSG, lpn));
    }
  }
}
