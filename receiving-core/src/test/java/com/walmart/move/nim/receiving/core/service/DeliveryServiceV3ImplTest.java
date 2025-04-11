package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DeliveryServiceV3ImplTest extends ReceivingTestBase {
  @InjectMocks private DeliveryServiceV3Impl deliveryServiceV3;
  private Gson gson;
  @Mock private RestConnector restConnector;
  @Mock private AppConfig appConfig;

  private GdmError gdmError;
  private final String gdmBaseUrl = "https://dev.gdm.prod.us.walmart.net";
  private Delivery delivery;
  private DeliveryUpdateMessage deliveryUpdateMessage;

  @BeforeClass
  private void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
    this.gson = new Gson();
    String dataPath =
        new File("../receiving-test/src/main/resources/json/GDMDeliveryDocumentV3.json")
            .getCanonicalPath();
    delivery = gson.fromJson(new String(Files.readAllBytes(Paths.get(dataPath))), Delivery.class);
    ReflectionTestUtils.setField(deliveryServiceV3, "gson", gson);

    deliveryUpdateMessage = new DeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryNumber("12345678");
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
  }

  @Test
  public void testGetGDMDatahappyPath() {
    try {
      doReturn(new ResponseEntity<>(gson.toJson(delivery), HttpStatus.OK))
          .when(restConnector)
          .exchange(any(), any(), any(), eq(String.class));

      Delivery deliveryResponse = deliveryServiceV3.getGDMData(deliveryUpdateMessage);
      assertEquals(deliveryResponse.getDeliveryNumber(), delivery.getDeliveryNumber());
      assertEquals(deliveryResponse.getDoorNumber(), delivery.getDoorNumber());
      assertEquals(
          deliveryResponse.getPurchaseOrders().get(0).getPoNumber(),
          delivery.getPurchaseOrders().get(0).getPoNumber());
      assertEquals(
          deliveryResponse.getPurchaseOrders().get(0).getLines().get(0).getPoLineNumber(),
          delivery.getPurchaseOrders().get(0).getLines().get(0).getPoLineNumber());
    } catch (ReceivingException e) {
      assertTrue(false);
    }
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void testExceptionCase() throws ReceivingException {
    doReturn(new ResponseEntity<>(null, HttpStatus.OK))
        .when(restConnector)
        .exchange(any(), any(), any(), eq(String.class));

    deliveryServiceV3.getGDMData(deliveryUpdateMessage);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testUnAccessableExceptionCase() throws ReceivingException {
    doThrow(ResourceAccessException.class)
        .when(restConnector)
        .exchange(any(), any(), any(), eq(String.class));

    deliveryServiceV3.getGDMData(deliveryUpdateMessage);
  }
}
