package com.walmart.move.nim.receiving.mfc.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;
import com.walmart.move.nim.receiving.mfc.common.MFCTestUtils;
import com.walmart.move.nim.receiving.mfc.message.publisher.ShipmentArrivalPublisher;
import com.walmart.move.nim.receiving.mfc.model.gdm.ScanPalletRequest;
import com.walmart.move.nim.receiving.mfc.model.ngr.NGRShipment;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import com.walmart.move.nim.receiving.mfc.transformer.NGRShipmentTransformer;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashMap;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ShipmentFinanceProcessorTest {

  @InjectMocks private ShipmentFinanceProcessor shipmentFinanceProcessor;

  @Mock private MFCDeliveryService deliveryService;

  @Mock private NGRShipmentTransformer ngrShipmentTransformer;

  @Mock private ShipmentArrivalPublisher kafkaShipmentArrivalPublisher;

  private static final long DELIVERY_NUM = 560135L;
  private static final String TRACKING_ID = "300008760310160015";
  private ASNDocument asnDocument;
  private NGRShipment ngrShipment;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(shipmentFinanceProcessor, "deliveryService", deliveryService);
    ReflectionTestUtils.setField(
        shipmentFinanceProcessor, "ngrShipmentTransformer", ngrShipmentTransformer);
    ReflectionTestUtils.setField(
        shipmentFinanceProcessor, "kafkaShipmentArrivalPublisher", kafkaShipmentArrivalPublisher);
    asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json");
    ngrShipment = ngrShipmentTransformer.transform(asnDocument);
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(deliveryService);
    Mockito.reset(ngrShipmentTransformer);
    Mockito.reset(kafkaShipmentArrivalPublisher);
  }

  @Test
  public void testInitiateFinance() {
    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json");

    DeliveryUpdateMessage deliveryUpdateMessage = new DeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryNumber("123");
    deliveryUpdateMessage.setShipmentDocumentId("456");

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(JacksonParser.writeValueAsString(deliveryUpdateMessage))
            .name(ReceivingConstants.SHIPMENT_FINANCE_PROCESSOR)
            .additionalAttributes(new HashMap<>()) // Additional attributes, if any
            .processor(ReceivingConstants.SHIPMENT_FINANCE_PROCESSOR)
            .build();

    ContainerScanRequest containerScanRequest =
        ContainerScanRequest.builder()
            .asnDocument(asnDocument)
            .trackingId(TRACKING_ID)
            .deliveryNumber(DELIVERY_NUM)
            .build();
    when(deliveryService.getShipmentDataFromGDM(anyLong(), anyString())).thenReturn(asnDocument);

    when(ngrShipmentTransformer.transform(any(ASNDocument.class))).thenReturn(ngrShipment);

    doNothing().when(kafkaShipmentArrivalPublisher).publish(any(NGRShipment.class));

    shipmentFinanceProcessor.doExecute(receivingEvent);

    verify(deliveryService, never())
        .findDeliveryDocumentByPalletAndDelivery(any(ScanPalletRequest.class), anyBoolean());
    verify(ngrShipmentTransformer, times(2)).transform(any(ASNDocument.class));
  }
}
