package com.walmart.move.nim.receiving.rx;

import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.model.ShipmentDetails;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.rx.service.ShipmentSelectorService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ShipmentSelectorTest {

  @Mock private ReceiptCustomRepository receiptCustomRepository;

  @InjectMocks private ShipmentSelectorService shipmentSelector;

  @BeforeClass
  public void setClass() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void afterMethod() {
    reset(receiptCustomRepository);
  }

  @Test
  public void test_autoSelectShipment() {

    doReturn(Collections.emptyList())
        .when(receiptCustomRepository)
        .receivedQtySummaryByShipmentNumberForPoAndPoLine(anyString(), anyInt());

    ShipmentDetails result =
        shipmentSelector.autoSelectShipment(getMockDeliveryDocumentLine_single_shipment());
    Assert.assertEquals(result.getShipmentNumber(), "0987");

    verify(receiptCustomRepository, times(1))
        .receivedQtySummaryByShipmentNumberForPoAndPoLine(anyString(), anyInt());
  }

  @Test
  public void test_autoSelectShipment_multiple_shipment() {

    doAnswer(
            new Answer<List>() {
              public List answer(InvocationOnMock invocation) {
                return Arrays.asList(new ReceiptSummaryResponse(2l, "inboundShipmentId1"));
              }
            })
        .when(receiptCustomRepository)
        .receivedQtySummaryByShipmentNumberForPoAndPoLine(anyString(), anyInt());

    ShipmentDetails result =
        shipmentSelector.autoSelectShipment(getMockDeliveryDocumentLine_multiple_shipment());
    Assert.assertEquals(result.getInboundShipmentDocId(), "inboundShipmentId2");

    verify(receiptCustomRepository, times(1))
        .receivedQtySummaryByShipmentNumberForPoAndPoLine(anyString(), anyInt());
  }

  private DeliveryDocumentLine getMockDeliveryDocumentLine_single_shipment() {
    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();

    mockDeliveryDocumentLine.setPurchaseReferenceNumber("123456");
    mockDeliveryDocumentLine.setPurchaseReferenceLineNumber(1);

    ShipmentDetails mockShipmentDetails = new ShipmentDetails();
    mockShipmentDetails.setShipmentNumber("0987");
    mockShipmentDetails.setShippedQty(2);
    mockShipmentDetails.setShippedQtyUom(ReceivingConstants.Uom.EACHES);

    mockDeliveryDocumentLine.setShipmentDetailsList(Arrays.asList(mockShipmentDetails));

    return mockDeliveryDocumentLine;
  }

  private DeliveryDocumentLine getMockDeliveryDocumentLine_multiple_shipment() {
    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();

    mockDeliveryDocumentLine.setPurchaseReferenceNumber("123456");
    mockDeliveryDocumentLine.setPurchaseReferenceLineNumber(1);

    ShipmentDetails mockShipmentDetails = new ShipmentDetails();
    mockShipmentDetails.setShipmentNumber("0987");
    mockShipmentDetails.setShippedQty(2);
    mockShipmentDetails.setShippedQtyUom(ReceivingConstants.Uom.EACHES);
    mockShipmentDetails.setInboundShipmentDocId("inboundShipmentId1");

    ShipmentDetails mockShipmentDetails2 = new ShipmentDetails();
    mockShipmentDetails2.setShipmentNumber("0988");
    mockShipmentDetails2.setShippedQty(10);
    mockShipmentDetails2.setShippedQtyUom(ReceivingConstants.Uom.EACHES);
    mockShipmentDetails.setInboundShipmentDocId("inboundShipmentId2");

    mockDeliveryDocumentLine.setShipmentDetailsList(
        Arrays.asList(mockShipmentDetails, mockShipmentDetails2));

    return mockDeliveryDocumentLine;
  }
}
