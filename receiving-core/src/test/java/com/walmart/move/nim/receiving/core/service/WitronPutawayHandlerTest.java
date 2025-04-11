package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.mock.data.WitronContainer;
import com.walmart.move.nim.receiving.core.model.PutawayMessage;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class WitronPutawayHandlerTest extends ReceivingTestBase {

  @InjectMocks private WitronPutawayHandler witronPutawayHandler;
  @Mock private JmsPublisher jmsPublisher;

  private final Container container1 = WitronContainer.getContainer1();

  private Container container2, container3, container4;
  private final HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

  private final Long deliveryNumber = 1234L;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    container2 = new Container();
    container2.setDeliveryNumber(deliveryNumber);
    container2.setContainerType("Peco");
    container2.setMessageId("111111111111111112");
    container2.setTrackingId("a32987000002");
    container2.setLocation("102");

    container3 = new Container();
    container3.setDeliveryNumber(deliveryNumber);
    container3.setContainerType("iGPS Pallet");
    container3.setMessageId("111111111111111113");
    container3.setTrackingId("a32987000003");
    container3.setLocation("103");
    container3.setOrgUnitId("1");

    container4 = new Container();
    container4.setDeliveryNumber(deliveryNumber);
    container4.setContainerType("RM2");
    container4.setMessageId("111111111111111113");
    container4.setTrackingId("a32987000003");
    container4.setLocation("104");
  }

  @AfterMethod
  public void tearDown() {
    reset(jmsPublisher);
  }

  @Test
  public void testPublishPutaway() {

    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));

    witronPutawayHandler.publishPutaway(container1, PUTAWAY_ADD_ACTION, httpHeaders);

    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
  }

  @Test
  public void testPublishPutaway_ForWitronInitiatedRequests_STOP_Publish() {
    HttpHeaders witronInitiatedRequestHeaders = MockHttpHeaders.getHeaders();
    witronInitiatedRequestHeaders.remove(REQUEST_ORIGINATOR);
    witronInitiatedRequestHeaders.add(REQUEST_ORIGINATOR, SOURCE_APP_NAME_WITRON);

    witronPutawayHandler.publishPutaway(
        container1, PUTAWAY_ADD_ACTION, witronInitiatedRequestHeaders);
    witronPutawayHandler.publishPutaway(
        container1, PUTAWAY_DELETE_ACTION, witronInitiatedRequestHeaders);
    witronPutawayHandler.publishPutaway(
        container1, PUTAWAY_UPDATE_ACTION, witronInitiatedRequestHeaders);

    verify(jmsPublisher, times(0))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
  }

  @Test
  public void testPublishPutaway_ForNonWitronInitiatedRequests() {

    HttpHeaders witronInitiatedRequestHeaders = MockHttpHeaders.getHeaders();

    witronPutawayHandler.publishPutaway(
        container1, PUTAWAY_ADD_ACTION, witronInitiatedRequestHeaders);
    witronPutawayHandler.publishPutaway(
        container1, PUTAWAY_DELETE_ACTION, witronInitiatedRequestHeaders);
    witronPutawayHandler.publishPutaway(
        container1, PUTAWAY_UPDATE_ACTION, witronInitiatedRequestHeaders);

    verify(jmsPublisher, times(3))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
  }

  @Test
  public void testPreparePutwayMessage_add() {

    PutawayMessage response =
        witronPutawayHandler.preparePutwayMessage(container1, PUTAWAY_ADD_ACTION, httpHeaders);

    assertEquals(response.getAction(), PUTAWAY_ADD_ACTION);
    assertEquals(response.getContainerType(), "13");
    assertEquals(response.getHeader().getEventType(), ReceivingConstants.PUTAWAY_EVENT_TYPE);
    assertEquals(response.getHeader().getMessageId(), container1.getMessageId());
    assertEquals(response.getDeliveryNumber(), container1.getDeliveryNumber().toString());
    assertEquals(response.getWeight(), (float) 160.0);
    assertEquals(response.getWeightUOM(), "LB");
    assertEquals(
        response.getHeader().getCorrelationId(),
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
  }

  @Test
  public void testPreparePutwayMessage_delete() {

    PutawayMessage response =
        witronPutawayHandler.preparePutwayMessage(
            container2, ReceivingConstants.PUTAWAY_DELETE_ACTION, httpHeaders);

    assertEquals(response.getAction(), ReceivingConstants.PUTAWAY_DELETE_ACTION);
    assertEquals(response.getContainerType(), "19");
    assertEquals(response.getHeader().getEventType(), ReceivingConstants.PUTAWAY_EVENT_TYPE);
    assertEquals(response.getHeader().getMessageId(), container2.getMessageId());
    assertEquals(response.getDeliveryNumber(), container2.getDeliveryNumber().toString());
    assertEquals(
        response.getHeader().getCorrelationId(),
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
  }

  @Test
  public void testPreparePutwayMessageForIGPS() {

    PutawayMessage response =
        witronPutawayHandler.preparePutwayMessage(container3, PUTAWAY_ADD_ACTION, httpHeaders);

    assertEquals(response.getAction(), PUTAWAY_ADD_ACTION);
    assertEquals(response.getContainerType(), "21");
    assertEquals(response.getHeader().getEventType(), ReceivingConstants.PUTAWAY_EVENT_TYPE);
    assertEquals(response.getHeader().getMessageId(), container3.getMessageId());
    assertEquals(response.getDeliveryNumber(), container3.getDeliveryNumber().toString());
    assertEquals(
        response.getHeader().getCorrelationId(),
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
  }

  @Test
  public void testPublishPutawaySequentially() {
    doNothing().when(jmsPublisher).publishSequentially(anyString(), any(), any(Boolean.class));

    witronPutawayHandler.publishPutawaySequentially(container1, container2, httpHeaders);
    verify(jmsPublisher, times(1)).publishSequentially(anyString(), any(), any(Boolean.class));
  }

  @Test
  public void test_getZeroPrefixPaddedNineDigitVendor() {
    Integer test1_number = 332767930; // 9 digits
    String test1_string = "332767930";
    assertEquals(
        witronPutawayHandler.getZeroPrefixPaddedNineDigitVendor(test1_number), test1_string);

    Integer test2_number = 53708940; // 8 digits
    String test2_string = "053708940";
    assertEquals(
        witronPutawayHandler.getZeroPrefixPaddedNineDigitVendor(test2_number), test2_string);

    Integer test3_number = 141; // type 29 vendor number is all zero
    String test3_string = "000000141"; // type 29 vendor number is all zero
    assertEquals(
        witronPutawayHandler.getZeroPrefixPaddedNineDigitVendor(test3_number), test3_string);

    Integer test4_number = null;
    assertNull(witronPutawayHandler.getZeroPrefixPaddedNineDigitVendor(test4_number));
  }

  @Test
  public void testPreparePutawayMessageForRM2() {

    PutawayMessage response =
        witronPutawayHandler.preparePutwayMessage(container4, PUTAWAY_ADD_ACTION, httpHeaders);

    assertEquals(response.getAction(), PUTAWAY_ADD_ACTION);
    assertEquals(response.getContainerType(), "28");
    assertEquals(response.getHeader().getEventType(), ReceivingConstants.PUTAWAY_EVENT_TYPE);
    assertEquals(response.getHeader().getMessageId(), container4.getMessageId());
    assertEquals(response.getDeliveryNumber(), container4.getDeliveryNumber().toString());
    assertEquals(
        response.getHeader().getCorrelationId(),
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
  }
}
