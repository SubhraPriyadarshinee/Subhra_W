package com.walmart.move.nim.receiving.rdc.message.publisher;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaDeliveryMessagePublisher;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("unchecked")
public class RdcMessagePublisherTest {

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private KafkaDeliveryMessagePublisher kafkaDeliveryMessagePublisher;

  @InjectMocks private RdcMessagePublisher rdcMessagePublisher;

  private Gson gson =
      new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();

  @BeforeClass
  public void setUpBeforeClass() throws Exception {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32818);
  }

  @AfterMethod
  public void afterMethod() {
    reset(tenantSpecificConfigReader, kafkaDeliveryMessagePublisher);
  }

  @Test
  public void test_publishDeliveryStatusByDeliveryInfo_Success() throws ReceivingException {

    doReturn(kafkaDeliveryMessagePublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    doNothing().when(kafkaDeliveryMessagePublisher).publish(any(), any());

    rdcMessagePublisher.publishDeliveryStatus(getMockDeliveryInfo(), getMessageHeaders());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(kafkaDeliveryMessagePublisher, times(1)).publish(any(), any());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_publishDeliveryStatusByDeliveryInfo_MissingMandatoryFields_Exception()
      throws ReceivingException {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(1234567l);
    deliveryInfo.setDeliveryStatus(DeliveryStatus.COMPLETE.name());
    rdcMessagePublisher.publishDeliveryStatus(deliveryInfo, getMessageHeaders());
  }

  @Test
  public void test_publishDeliveryStatusByDeliveryInfo_TagComplete() throws ReceivingException {

    doReturn(kafkaDeliveryMessagePublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    doNothing().when(kafkaDeliveryMessagePublisher).publish(any(), any());

    rdcMessagePublisher.publishDeliveryStatus(getMockDeliveryInfo_DockTag(), getMessageHeaders());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(kafkaDeliveryMessagePublisher, times(1)).publish(any(), any());
  }

  @Test
  public void test_publishDeliveryStatus_With_DeliveryNumber() throws ReceivingException {

    doReturn(kafkaDeliveryMessagePublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    doNothing().when(kafkaDeliveryMessagePublisher).publish(any(), any());

    DeliveryInfo deliveryInfo =
        rdcMessagePublisher.publishDeliveryStatus(
            12345L, DeliveryStatus.COMPLETE.name(), getMessageHeaders());
    assertNotNull(deliveryInfo);
    assertEquals(deliveryInfo.getDeliveryNumber(), Long.valueOf(12345));

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(kafkaDeliveryMessagePublisher, times(1)).publish(any(), any());
  }

  @Test
  public void test_publishDeliveryReceipts() throws ReceivingException {

    doReturn(kafkaDeliveryMessagePublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    doNothing().when(kafkaDeliveryMessagePublisher).publish(any(), any());

    rdcMessagePublisher.publishDeliveryReceipts(getMockOsdrSummary(), getMessageHeaders());

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(kafkaDeliveryMessagePublisher, times(1)).publish(any(), any());
  }

  private static DeliveryInfo getMockDeliveryInfo() {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(2222323l);
    deliveryInfo.setTrailerNumber("22232");
    deliveryInfo.setDoorNumber("12");
    deliveryInfo.setDeliveryStatus("OPEN");
    return deliveryInfo;
  }

  private static DeliveryInfo getMockDeliveryInfo_DockTag() {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(2222323l);
    deliveryInfo.setDeliveryStatus(DeliveryStatus.TAG_COMPLETE.name());
    deliveryInfo.setRemainingTags(3);
    deliveryInfo.setTagCount(5);
    return deliveryInfo;
  }

  private static OsdrSummary getMockOsdrSummary() {
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setDeliveryNumber(2222323l);
    osdrSummary.setTrailerNumber("22232");
    osdrSummary.setDoorNumber("12");
    osdrSummary.setDeliveryStatus("OPEN");
    return osdrSummary;
  }

  private static Map<String, Object> getMessageHeaders() {
    Map<String, Object> headers = MockMessageHeaders.getHeadersMap();
    headers.put(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    return headers;
  }
}
