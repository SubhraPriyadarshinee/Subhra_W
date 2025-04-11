package com.walmart.move.nim.receiving.witron.service;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryItemOverrideService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.witron.mock.data.MockInstruction;
import java.util.Date;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class GdcRefreshInstructionHandlerTest {
  @InjectMocks private GdcRefreshInstructionHandler gdcRefreshInstructionHandler;
  @Mock private ReceiptService receiptService;

  @Mock private InstructionRepository instructionRepository;

  @Mock private DeliveryItemOverrideService deliveryItemOverrideService;

  @Mock private TenantSpecificConfigReader configUtils;

  private HttpHeaders headers;
  private final Gson gson = new Gson();
  private static final String countryCode = "US";
  private static final String facilityNum = "32612";

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);
    reset(configUtils, deliveryItemOverrideService, instructionRepository, receiptService);

    ReflectionTestUtils.setField(gdcRefreshInstructionHandler, "gson", gson);
  }

  @AfterMethod
  public void resetMocks() {
    reset(receiptService);
  }

  @Test
  public void testRefreshInstruction_ReturnsInstructionResponse_with_item_override()
      throws ReceivingException {
    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setDeliveryNumber(12341234l);
    deliveryItemOverride.setItemNumber(12341234l);
    deliveryItemOverride.setTempPalletHi(3);
    deliveryItemOverride.setTempPalletTi(4);
    deliveryItemOverride.setLastChangedUser("test");
    deliveryItemOverride.setLastChangedTs(new Date());

    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    when(deliveryItemOverrideService.findByDeliveryNumberAndItemNumber(anyLong(), anyLong()))
        .thenReturn(Optional.of(deliveryItemOverride));
    when(instructionRepository.save(any())).thenReturn(MockInstruction.getInstruction());
    when(configUtils.isDeliveryItemOverrideEnabled(any())).thenReturn(true);

    InstructionResponse instructionResponse =
        gdcRefreshInstructionHandler.refreshInstruction(MockInstruction.getInstruction(), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getDeliveryDocuments());
    DeliveryDocumentLine deliveryDocumentLine =
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    assert deliveryDocumentLine.getPalletTie() == 4;
    assert deliveryDocumentLine.getPalletHigh() == 3;
    assertNotNull(
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0));
    assertEquals(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getWarehouseAreaDesc(),
        "Dry Produce");

    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
  }

  @Test
  public void testRefreshInstruction_ReturnsInstructionResponse_no_item_override()
      throws ReceivingException {

    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    when(deliveryItemOverrideService.findByDeliveryNumberAndItemNumber(anyLong(), anyLong()))
        .thenReturn(Optional.empty());
    when(instructionRepository.save(any())).thenReturn(MockInstruction.getInstruction());
    when(configUtils.isDeliveryItemOverrideEnabled(any())).thenReturn(true);

    InstructionResponse instructionResponse =
        gdcRefreshInstructionHandler.refreshInstruction(MockInstruction.getInstruction(), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getDeliveryDocuments());
    DeliveryDocumentLine deliveryDocumentLine =
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    assert deliveryDocumentLine.getPalletTie() == 30;
    assert deliveryDocumentLine.getPalletHigh() == 4;
    assertNotNull(
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0));
    assertEquals(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getWarehouseAreaDesc(),
        "Dry Produce");

    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
  }
}
