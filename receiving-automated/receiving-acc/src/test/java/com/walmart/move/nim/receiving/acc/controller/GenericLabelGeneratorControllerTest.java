package com.walmart.move.nim.receiving.acc.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.walmart.move.nim.receiving.acc.service.GenericLabelGeneratorService;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.label.FormattedLabels;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.util.Collections;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class GenericLabelGeneratorControllerTest extends ReceivingControllerTestBase {

  private MockMvc mockMvc;

  private GenericLabelGeneratorController genericLabelGeneratorController;

  private RestResponseExceptionHandler restResponseExceptionHandler;

  @Mock private GenericLabelGeneratorService genericLabelGeneratorService;

  @Autowired private ResourceBundleMessageSource resourceBundleMessageSource;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    genericLabelGeneratorController = new GenericLabelGeneratorController();

    restResponseExceptionHandler = new RestResponseExceptionHandler();

    ReflectionTestUtils.setField(
        restResponseExceptionHandler, "resourceBundleMessageSource", resourceBundleMessageSource);

    ReflectionTestUtils.setField(
        genericLabelGeneratorController,
        "genericLabelGeneratorService",
        genericLabelGeneratorService);

    this.mockMvc =
        MockMvcBuilders.standaloneSetup(genericLabelGeneratorController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
  }

  @Test
  public void testGetExceptionLabel_failure() throws Exception {
    when(genericLabelGeneratorService.generateExceptionLabel(anyLong(), anyString()))
        .thenThrow(
            new ReceivingBadDataException(
                ExceptionCodes.PRE_GEN_DATA_NOT_FOUND, ReceivingException.PRE_GEN_DATA_NOT_FOUND));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get(
                    "/label-gen/deliveries/94769060/upcs/10074451115207/exceptionLabels")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testGetExceptionLabel_success() throws Exception {
    reset(genericLabelGeneratorService);
    when(genericLabelGeneratorService.generateExceptionLabel(anyLong(), anyString()))
        .thenReturn(
            FormattedLabels.builder()
                .lpns(Collections.singletonList("c32987000000000000000007"))
                .build());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get(
                    "/label-gen/deliveries/94769060/upcs/10074451115207/exceptionLabels")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
  }
}
