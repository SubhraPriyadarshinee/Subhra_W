package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.UUID;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DefaultRefreshInstructionHandlerTest {
  @InjectMocks private DefaultRefreshInstructionHandler defaultRefreshInstructionHandler;
  @Mock private ReceiptService receiptService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks @Spy private DefaultOpenQtyCalculator defaultOpenQtyCalculator;
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
    ReflectionTestUtils.setField(defaultRefreshInstructionHandler, "gson", gson);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class)))
        .thenReturn(defaultOpenQtyCalculator);
  }

  @Test
  public void testRefreshInstruction() throws ReceivingException {

    headers.set(IS_KOTLIN_CLIENT, "false");

    InstructionResponse instructionResponse =
        defaultRefreshInstructionHandler.refreshInstruction(
            MockInstruction.getInstruction(), headers);
    assertNull(instructionResponse);
  }

  @Test
  public void testRefreshInstruction_ReturnsInstructionResponse() throws ReceivingException {
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    headers.set(IS_KOTLIN_CLIENT, "true");

    InstructionResponse instructionResponse =
        defaultRefreshInstructionHandler.refreshInstruction(
            MockInstruction.getInstruction(), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertNotNull(
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0));

    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
  }

  @Test
  public void testRefreshInstruction_for_POCON_ReturnsInstructionResponse()
      throws ReceivingException {
    when(receiptService.getReceivedQtyByPoAndDeliveryNumber(anyString(), anyLong()))
        .thenReturn(Long.parseLong("0"));

    headers.set(IS_KOTLIN_CLIENT, "true");

    InstructionResponse instructionResponse =
        defaultRefreshInstructionHandler.refreshInstruction(
            MockInstruction.getInstructionForPOCON(), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertNotNull(
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0));

    verify(receiptService, times(1)).getReceivedQtyByPoAndDeliveryNumber(anyString(), anyLong());
  }

  private void setTenantContextForHeader() {
    TenantContext.clear();
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
  }
}
