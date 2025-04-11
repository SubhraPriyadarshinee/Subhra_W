package com.walmart.move.nim.receiving.mfc.transformer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.mfc.common.MFCTestUtils;
import com.walmart.move.nim.receiving.mfc.model.ngr.NGRShipment;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class NGRShipmentTransformerTest {

  @InjectMocks private NGRShipmentTransformer ngrShipmentTransformer;
  SimpleDateFormat format;

  @BeforeClass
  private void init() {
    MockitoAnnotations.initMocks(this);
    format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ENGLISH);
  }

  @Test
  public void testASNDocumentToNGRShipmentTransform() throws ParseException {
    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocumentWithMulitplePacks.json");
    NGRShipment ngrShipment = ngrShipmentTransformer.transform(asnDocument);

    assertNotNull(ngrShipment);
    assertNotNull(ngrShipment.getDelivery());
    assertNotNull(ngrShipment.getShipment());
    assertEquals("55040543", ngrShipment.getDelivery().getDeliveryNumber().toString());
    assertEquals(
        format.parse("2022-09-09T12:12:47.596-0000"),
        ngrShipment.getDelivery().getArrivalTimeStamp());
    assertEquals(
        format.parse("2022-09-08T12:12:47.596-0000"), ngrShipment.getDelivery().getScheduled());
    assertEquals("ARV", ngrShipment.getDelivery().getStatusInformation().getStatus());

    assertEquals("6035", ngrShipment.getShipment().getSource().getNumber());
    assertEquals("WALMART", ngrShipment.getShipment().getSource().getNumberType());
    assertEquals("DC", ngrShipment.getShipment().getSource().getType());
    assertEquals("US", ngrShipment.getShipment().getSource().getCountryCode());
    assertEquals("WM", ngrShipment.getShipment().getSource().getShipperName());

    assertEquals("5504", ngrShipment.getShipment().getDestination().getNumber());
    assertEquals("STORE", ngrShipment.getShipment().getDestination().getType());
    assertEquals("US", ngrShipment.getShipment().getDestination().getCountryCode());

    assertEquals("57473734", ngrShipment.getShipment().getShipmentDetail().getTrailerNumber());
    assertEquals("NI-214", ngrShipment.getShipment().getShipmentDetail().getLoadNumber());
  }
}
