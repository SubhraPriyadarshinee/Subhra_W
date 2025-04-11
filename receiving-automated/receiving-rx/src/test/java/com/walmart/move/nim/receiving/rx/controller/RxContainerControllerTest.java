package com.walmart.move.nim.receiving.rx.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.SerializedContainerUpdateRequest;
import com.walmart.move.nim.receiving.rx.service.RxGetContainerRequestHandler;
import com.walmart.move.nim.receiving.rx.service.RxUpdateSerializedContainerQtyRequestHandler;
import java.util.Arrays;
import java.util.Collections;
import org.apache.commons.codec.digest.DigestUtils;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RxContainerControllerTest extends ReceivingControllerTestBase {

  private MockMvc mockMvc;
  private RestResponseExceptionHandler restResponseExceptionHandler;

  @Autowired @MockBean private ContainerService containerService;
  @Autowired @MockBean private RxGetContainerRequestHandler rxGetContainerRequestHandler;

  @Autowired @MockBean
  private RxUpdateSerializedContainerQtyRequestHandler rxUpdateSerializedContainerQtyRequestHandler;

  @InjectMocks RxContainerController rxContainerController;
  @Autowired private ResourceBundleMessageSource resourceBundleMessageSource;

  private HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    restResponseExceptionHandler = new RestResponseExceptionHandler();

    ReflectionTestUtils.setField(
        restResponseExceptionHandler, "resourceBundleMessageSource", resourceBundleMessageSource);

    mockMvc =
        MockMvcBuilders.standaloneSetup(rxContainerController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
  }

  @Test
  public void testDeleteContainers_success() throws Exception {
    doNothing().when(containerService).deleteContainers(anyList(), any(HttpHeaders.class));
    String trackingIdParam = "trackingIds=MOCK_TRACKING_ID1,MOCK_TRACKING_ID2,MOCK_TRACKING_ID3";
    String hashValue = DigestUtils.md5Hex(trackingIdParam + "receiving-secret").toUpperCase();
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/containers?" + trackingIdParam + "&hash=" + hashValue)
                .headers(httpHeaders))
        .andExpect(status().is2xxSuccessful());
  }

  @Test
  public void testDeleteContainers_failure() throws Exception {
    doNothing().when(containerService).deleteContainers(anyList(), any(HttpHeaders.class));
    mockMvc
        .perform(MockMvcRequestBuilders.delete("/containers").headers(httpHeaders))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testDeleteContainers_invalid_hash() throws Exception {
    doNothing().when(containerService).deleteContainers(anyList(), any(HttpHeaders.class));
    String trackingIdParam = "trackingIds=MOCK_TRACKING_ID1,MOCK_TRACKING_ID2,MOCK_TRACKING_ID3";
    String hashValue = DigestUtils.md5Hex(trackingIdParam + "receiving-wrong-secret").toUpperCase();
    String response =
        mockMvc
            .perform(
                MockMvcRequestBuilders.delete(
                        "/containers?" + trackingIdParam + "&hash=" + hashValue)
                    .headers(httpHeaders))
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Gson gson = new Gson();
    ReceivingBadDataException receivingBadDataException =
        gson.fromJson(response, ReceivingBadDataException.class);
    assertEquals(receivingBadDataException.getErrorCode(), ExceptionCodes.INVALID_HASH_VALUE_SENT);
    assertEquals(receivingBadDataException.getDescription(), RxConstants.INVALID_HASH_VALUE_SENT);
  }

  @Test
  public void test_getContainersSummary_success() throws Exception {
    doReturn(Collections.emptyList())
        .when(rxGetContainerRequestHandler)
        .getContainersSummary(anyLong(), anyString(), anyString());
    long instructionId = 1l;
    String serial = "MOCK_SERIAL";
    String lotNumber = "MOCK_LOT_NUMBER";
    mockMvc
        .perform(
            MockMvcRequestBuilders.get(
                    "/containers/summary?instructionId="
                        + instructionId
                        + "&serial="
                        + serial
                        + "&lotNumber="
                        + lotNumber)
                .headers(httpHeaders))
        .andExpect(status().is2xxSuccessful());

    verify(rxGetContainerRequestHandler, times(1))
        .getContainersSummary(anyLong(), anyString(), anyString());
  }

  @Test
  public void test_updateQuantityByTrackingId_success() throws Exception {
    doReturn(new ContainerUpdateResponse())
        .when(rxUpdateSerializedContainerQtyRequestHandler)
        .updateQuantityByTrackingId(
            anyString(), any(SerializedContainerUpdateRequest.class), any(HttpHeaders.class));
    String trackingId = "MOCK_TRACKING_ID";
    SerializedContainerUpdateRequest mockContainerUpdateRequest =
        new SerializedContainerUpdateRequest();
    mockContainerUpdateRequest.setAdjustQuantity(10);
    mockContainerUpdateRequest.setTrackingIds(Arrays.asList("MOCK_CHILD_TRACKING_ID"));
    String lotNumber = "MOCK_LOT_NUMBER";
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(
                    "/containers/MOCK_TRACKING_ID/adjust?serializedContainer=true")
                .content(new Gson().toJson(mockContainerUpdateRequest))
                .headers(httpHeaders))
        .andExpect(status().is2xxSuccessful());

    verify(rxUpdateSerializedContainerQtyRequestHandler, times(1))
        .updateQuantityByTrackingId(
            anyString(), any(SerializedContainerUpdateRequest.class), any(HttpHeaders.class));
  }
}
