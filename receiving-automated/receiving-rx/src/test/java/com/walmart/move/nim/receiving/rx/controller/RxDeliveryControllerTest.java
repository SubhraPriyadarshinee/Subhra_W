package com.walmart.move.nim.receiving.rx.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.fail;

import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rx.builders.RxSSCCValidator;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryServiceImpl;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RxDeliveryControllerTest extends ReceivingControllerTestBase {

  private MockMvc mockMvc;
  private RestResponseExceptionHandler restResponseExceptionHandler;

  @InjectMocks RxDeliveryController rxDeliveryController;
  @Autowired private ResourceBundleMessageSource resourceBundleMessageSource;

  @Mock private RxSSCCValidator rxSSCCValidator;
  @Mock private RxDeliveryServiceImpl rxDeliveryServiceImpl;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    restResponseExceptionHandler = new RestResponseExceptionHandler();

    ReflectionTestUtils.setField(
        restResponseExceptionHandler, "resourceBundleMessageSource", resourceBundleMessageSource);

    mockMvc =
        MockMvcBuilders.standaloneSetup(rxDeliveryController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
  }

  @Test
  public void testGetValidSSCCResponse() throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      Optional<List<DeliveryDocumentLine>> response =
          Optional.ofNullable(
              Arrays.asList(
                  MockInstruction.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0)));
      when(rxSSCCValidator.validateScannedSSCC(anyLong(), anyString(), any(HttpHeaders.class)))
          .thenReturn(response);

      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/deliveries/10967345/shipments/00100700302232310001")
                  .headers(headers))
          .andExpect(status().is2xxSuccessful());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testGetValidSSCCResponse_error() throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      when(rxSSCCValidator.validateScannedSSCC(anyLong(), anyString(), any(HttpHeaders.class)))
          .thenThrow(
              new ReceivingBadDataException(
                  ExceptionCodes.GDM_SSCC_NOT_FOUND,
                  String.format(ReceivingConstants.GDM_SHIPMENT_NOT_FOUND, "00100700302232310001"),
                  "00100700302232310001"));

      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/deliveries/10967345/shipments/00100700302232310001")
                  .headers(headers))
          .andExpect(status().is4xxClientError());

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void test_prepareDeliveryLabelData_Response() throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    try {
      PrintLabelData response = new PrintLabelData();
      when(rxDeliveryServiceImpl.prepareDeliveryLabelData(
              anyLong(), anyInt(), any(HttpHeaders.class)))
          .thenReturn(response);

      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/deliveries/10967345/prepareLabel").headers(headers))
          .andExpect(status().is2xxSuccessful());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void test_prepareDeliveryLabelData_Response_error() throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      when(rxDeliveryServiceImpl.prepareDeliveryLabelData(
              anyLong(), anyInt(), any(HttpHeaders.class)))
          .thenThrow(new NullPointerException());

      mockMvc
          .perform(MockMvcRequestBuilders.get("/deliveries/10967345/prepareLabel").headers(headers))
          .andExpect(status().is4xxClientError());

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }
}
