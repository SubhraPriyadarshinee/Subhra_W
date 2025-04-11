package com.walmart.move.nim.receiving.rx.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.PatchInstructionRequest;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxInstructionControllerTest extends ReceivingControllerTestBase {
  private MockMvc mockMvc;
  private PatchInstructionRequest patchInstructionRequest;

  @InjectMocks private RxInstructionController rxInstructionController;
  @Mock private RxInstructionService rxInstructionService;

  @BeforeMethod
  public void setup() {
    patchInstructionRequest.setUpcNumber("mockUpc");
  }

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    patchInstructionRequest = new PatchInstructionRequest();
    mockMvc = MockMvcBuilders.standaloneSetup(rxInstructionController).build();
  }

  @Test
  public void testPatchInstructionResponseIsSuccess() throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      when(rxInstructionService.patchInstruction(anyLong(), any(), any(HttpHeaders.class)))
          .thenReturn(MockInstruction.getPatchedRxInstruction());

      mockMvc
          .perform(
              MockMvcRequestBuilders.patch("/instructions/12212")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(new Gson().toJson(patchInstructionRequest))
                  .headers(headers))
          .andExpect(status().is2xxSuccessful());
    } catch (Exception e) {
      assertNull(e);
    }
  }

  @Test
  public void testPatchInstructionResponseIsError() throws ReceivingException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    try {
      when(rxInstructionService.patchInstruction(anyLong(), any(), any(HttpHeaders.class)))
          .thenThrow(
              new ReceivingBadDataException(
                  ExceptionCodes.ERROR_IN_PATCHING_INSTRUCTION,
                  RxConstants.ERROR_IN_PATCHING_INSTRUCTION));

      mockMvc
          .perform(
              MockMvcRequestBuilders.patch("/instructions/12213")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(new Gson().toJson(patchInstructionRequest))
                  .headers(headers))
          .andExpect(status().is5xxServerError());

    } catch (Exception e) {
      assertNotNull(e);
    }
  }
}
