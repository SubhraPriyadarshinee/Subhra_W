package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.mock.data.MockInstructionResponse;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.io.IOException;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcRefreshInstructionHandlerTest {
  @InjectMocks private RdcRefreshInstructionHandler rdcRefreshInstructionHandler;
  @Mock private RdcInstructionUtils rdcInstructionUtils;

  private HttpHeaders headers;
  private final Gson gson = new Gson();
  private static final String countryCode = "US";
  private static final String facilityNum = "32818";

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);

    ReflectionTestUtils.setField(rdcRefreshInstructionHandler, "gson", gson);
  }

  @AfterMethod
  public void resetMocks() {
    reset(rdcInstructionUtils);
  }

  @Test
  public void testRefreshInstructionReturnsInstructionResponse() throws IOException {
    Instruction mockInstruction = MockInstructionResponse.getMockInstructionForRefresh();
    DeliveryDocument deliveryDocument =
        gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
    when(rdcInstructionUtils.validateExistingInstruction(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(new Pair<>(mockInstruction, Collections.singletonList(deliveryDocument)));

    InstructionResponse instructionResponse =
        rdcRefreshInstructionHandler.refreshInstruction(mockInstruction, headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getDeliveryDocuments());

    verify(rdcInstructionUtils, times(1))
        .validateExistingInstruction(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));
  }
}
