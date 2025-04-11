package com.walmart.move.nim.receiving.core.service;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.core.model.ReceivingType;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DefaultMultiSkuHandlerTest {

  @InjectMocks private DefaultMultiSkuHandler multiSkuHandler;
  private HttpHeaders headers;
  private final Gson gson = new Gson();
  private static final String countryCode = "US";
  private static final String facilityNum = "32987";

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);
  }

  @Test
  public void testHandleMultiSku() {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.SSCC.getReceivingType());
    InstructionResponse response =
        multiSkuHandler.handleMultiSku(
            true, instructionRequest, instructionResponse, new Instruction());
    assertNotNull(response.getInstruction());
    assertEquals(
        response.getInstruction().getInstructionCode(), ReceivingConstants.MULTI_SKU_INST_CODE);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Scanned label is multi item Pallet. Sort and receive by each Item")
  public void testHandleMultiSku_FeatureDisabled() {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.SSCC.getReceivingType());
    InstructionResponse response =
        multiSkuHandler.handleMultiSku(
            false, instructionRequest, instructionResponse, new Instruction());
    assertNull(response.getInstruction());
  }

  @Test
  public void testHandleMultiSku_NotSupportedMultiSkuScanType() {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.UPC.getReceivingType());
    InstructionResponse response =
        multiSkuHandler.handleMultiSku(
            false, instructionRequest, instructionResponse, new Instruction());
    assertNull(response.getInstruction());
  }
}
