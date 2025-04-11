package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_MANUAL_GDC_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.REQUEST_ORIGINATOR;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SOURCE_APP_NAME_WITRON;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.WITRON_HAWKEYE_PUBLISH_PUTAWAY_ENABLED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaWitronPutawayMessagePublisher;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.model.witron.WitronPutawayMessage;
import com.walmart.move.nim.receiving.core.service.WitronPutawayHandler;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class GdcPutawayPublisherTest {
  @InjectMocks private GdcPutawayPublisher gdcPutawayPublisher;
  @Mock private WitronPutawayHandler witronPutawayHandler;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private KafkaWitronPutawayMessagePublisher kafkaWitronPutawayMessagePublisher;

  private Container container;
  private HttpHeaders httpHeaders;

  @BeforeMethod
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);

    container = MockContainer.getContainer();
    httpHeaders = GdcHttpHeaders.getHeaders();
  }

  @AfterMethod
  public void tearDown() {
    reset(witronPutawayHandler);
    reset(kafkaWitronPutawayMessagePublisher);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void testPublishPutawayMessage_HawkeyeEnabled() throws ReceivingException {
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32612", IS_MANUAL_GDC_ENABLED, false);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32612", WITRON_HAWKEYE_PUBLISH_PUTAWAY_ENABLED, false);
    doNothing()
        .when(kafkaWitronPutawayMessagePublisher)
        .publish(any(WitronPutawayMessage.class), any());

    gdcPutawayPublisher.publishMessage(
        container, ReceivingConstants.PUTAWAY_ADD_ACTION, httpHeaders);

    verify(kafkaWitronPutawayMessagePublisher, times(1))
        .publish(any(WitronPutawayMessage.class), any());
    verify(witronPutawayHandler, times(0))
        .publishPutaway(any(Container.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testPublishPutawayMessage_HawkeyeDisabled() throws ReceivingException {
    when(tenantSpecificConfigReader.getPutawayServiceByFacility(anyString()))
        .thenReturn(witronPutawayHandler);
    doNothing()
        .when(witronPutawayHandler)
        .publishPutaway(any(Container.class), anyString(), any(HttpHeaders.class));

    gdcPutawayPublisher.publishMessage(
        container, ReceivingConstants.PUTAWAY_ADD_ACTION, httpHeaders);

    verify(witronPutawayHandler, times(1))
        .publishPutaway(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(kafkaWitronPutawayMessagePublisher, times(0))
        .publish(any(WitronPutawayMessage.class), any());
  }

  @Test
  public void testPublishPutawayMessage_MQ_witron_source() throws ReceivingException {
    when(tenantSpecificConfigReader.getPutawayServiceByFacility(anyString()))
        .thenReturn(witronPutawayHandler);
    doNothing()
        .when(witronPutawayHandler)
        .publishPutaway(any(Container.class), anyString(), any(HttpHeaders.class));
    HttpHeaders witronInitiatedRequestHeaders = MockHttpHeaders.getHeaders();
    witronInitiatedRequestHeaders.remove(REQUEST_ORIGINATOR);
    witronInitiatedRequestHeaders.add(REQUEST_ORIGINATOR, SOURCE_APP_NAME_WITRON);

    gdcPutawayPublisher.publishMessage(
        container, ReceivingConstants.PUTAWAY_ADD_ACTION, witronInitiatedRequestHeaders);
    gdcPutawayPublisher.publishMessage(
        container, ReceivingConstants.PUTAWAY_DELETE_ACTION, witronInitiatedRequestHeaders);
    gdcPutawayPublisher.publishMessage(
        container, ReceivingConstants.PUTAWAY_UPDATE_ACTION, witronInitiatedRequestHeaders);

    verify(witronPutawayHandler, times(0))
        .publishPutaway(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(kafkaWitronPutawayMessagePublisher, times(0))
        .publish(any(WitronPutawayMessage.class), any());
  }

  @Test
  public void testPublishPutawayMessage_kafka_Hawkeye_witron_source() throws ReceivingException {
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32612", IS_MANUAL_GDC_ENABLED, false);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32612", WITRON_HAWKEYE_PUBLISH_PUTAWAY_ENABLED, false);
    doNothing()
        .when(kafkaWitronPutawayMessagePublisher)
        .publish(any(WitronPutawayMessage.class), any());

    HttpHeaders witronInitiatedRequestHeaders = MockHttpHeaders.getHeaders();
    witronInitiatedRequestHeaders.remove(REQUEST_ORIGINATOR);
    witronInitiatedRequestHeaders.add(REQUEST_ORIGINATOR, SOURCE_APP_NAME_WITRON);

    gdcPutawayPublisher.publishMessage(
        container, ReceivingConstants.PUTAWAY_ADD_ACTION, witronInitiatedRequestHeaders);
    gdcPutawayPublisher.publishMessage(
        container, ReceivingConstants.PUTAWAY_DELETE_ACTION, witronInitiatedRequestHeaders);
    gdcPutawayPublisher.publishMessage(
        container, ReceivingConstants.PUTAWAY_UPDATE_ACTION, witronInitiatedRequestHeaders);

    verify(witronPutawayHandler, times(0))
        .publishPutaway(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(kafkaWitronPutawayMessagePublisher, times(0))
        .publish(any(WitronPutawayMessage.class), any());
  }
}
