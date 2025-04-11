package com.walmart.move.nim.receiving.rx.builders;

import static org.junit.Assert.assertNotNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.Test;

public class RxDeliveryLabelBuilderTest {

  private RxDeliveryLabelBuilder rxDeliveryLabelBuilder = new RxDeliveryLabelBuilder();

  @Test
  public void test_generateDeliveryLabel() {

    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    PrintLabelData deliveryLabelResponse =
        rxDeliveryLabelBuilder.generateDeliveryLabel(12345l, 10, httpHeaders);

    assertNotNull(deliveryLabelResponse);

    assertEquals(RxConstants.CLIENT_ID, deliveryLabelResponse.getClientId());

    assertTrue(MapUtils.isNotEmpty(deliveryLabelResponse.getHeaders()));
    assertEquals(
        "32898", deliveryLabelResponse.getHeaders().get(ReceivingConstants.TENENT_FACLITYNUM));
    assertEquals(
        "US", deliveryLabelResponse.getHeaders().get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertEquals(
        "3a2b6c1d2e",
        deliveryLabelResponse.getHeaders().get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    assertTrue(CollectionUtils.isNotEmpty(deliveryLabelResponse.getPrintRequests()));
    assertEquals("12345", deliveryLabelResponse.getPrintRequests().get(0).getLabelIdentifier());
    assertEquals(
        RxConstants.DELIVERY_LABEL_FORMAT_NAME,
        deliveryLabelResponse.getPrintRequests().get(0).getFormatName());
    assertEquals(72, deliveryLabelResponse.getPrintRequests().get(0).getTtlInHours());
    assertTrue(
        CollectionUtils.isNotEmpty(deliveryLabelResponse.getPrintRequests().get(0).getData()));

    assertEquals(
        "12345", deliveryLabelResponse.getPrintRequests().get(0).getData().get(0).getValue());
    assertEquals(
        RxConstants.DELIVERY_NUMBER,
        deliveryLabelResponse.getPrintRequests().get(0).getData().get(0).getKey());

    assertEquals("", deliveryLabelResponse.getPrintRequests().get(0).getData().get(1).getValue());
    assertEquals(
        RxConstants.LABEL_TIMESTAMP,
        deliveryLabelResponse.getPrintRequests().get(0).getData().get(1).getKey());

    assertEquals(
        "rxTestUser", deliveryLabelResponse.getPrintRequests().get(0).getData().get(2).getValue());
    assertEquals(
        RxConstants.USER,
        deliveryLabelResponse.getPrintRequests().get(0).getData().get(2).getKey());

    assertEquals(
        "rxTestUser", deliveryLabelResponse.getPrintRequests().get(9).getData().get(2).getValue());
    assertEquals(
        RxConstants.USER,
        deliveryLabelResponse.getPrintRequests().get(9).getData().get(2).getKey());
  }
}
