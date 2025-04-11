package com.walmart.move.nim.receiving.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.ManufactureDetail;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.ExceptionInfo;
import com.walmart.move.nim.receiving.rx.model.FixitAttpRequest;
import com.walmart.move.nim.receiving.rx.service.EpsicHelper;
import java.util.Arrays;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EpcisControllerTest extends ReceivingControllerTestBase {

  @InjectMocks private EpcisController epcisController;
  @Mock private EpsicHelper epsicHelper;

  private final Gson gson = new Gson();
  private MockMvc mockMvc;
  @InjectMocks private RestResponseExceptionHandler restResponseExceptionHandler;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.openMocks(this);
    ReflectionTestUtils.setField(epcisController, "epcisHelper", epsicHelper);
    this.mockMvc =
        MockMvcBuilders.standaloneSetup(epcisController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
  }

  @Test
  public void publishFixitEventsToAttp() throws Exception {
    // given
    FixitAttpRequest request = new FixitAttpRequest();
    ManufactureDetail details = new ManufactureDetail();
    details.setLot("12345678");
    details.setGtin("01123840356119");
    details.setSerial("SN345678");
    details.setExpiryDate("20-05-05");
    List<ManufactureDetail> scannedDetails = Arrays.asList(details);
    request.setScannedDataList(scannedDetails);
    ExceptionInfo info = new ExceptionInfo();
    info.setDisposition(RxConstants.DISPOSITION_DESTROY);
    request.setExceptionInfo(info);

    // when
    String response =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/publishEpcisEvent")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(gson.toJson(request))
                    .headers(MockHttpHeaders.getHeaders()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // then
    Assert.assertNotNull(response);
  }

  @Test
  public void publishFixitEventsToAttp_Invalid() throws Exception {
    // given
    FixitAttpRequest request = new FixitAttpRequest();
    doCallRealMethod().when(epsicHelper).validateRequest(any());

    // when
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/publishEpcisEvent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(request))
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().is4xxClientError());

    // then should throw receiving exception invalid fixit request
  }

  @Test
  public void publishFixitEventsToAttp_PublishError() throws Exception {
    // given
    FixitAttpRequest request = new FixitAttpRequest();
    doThrow(new ReceivingException("")).when(epsicHelper).publishFixitEventsToAttp(any(), any());

    // when
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/publishEpcisEvent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(request))
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().is4xxClientError());

    // then should throw receiving exception fixit publish
  }
}
