package com.walmart.move.nim.receiving.core.message.publisher;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.APP_NAME_VALUE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.REQUEST_ORIGINATOR;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.service.RapidRelayerService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmartlabs.hawkshaw.clients.generic.enricher.HawkshawEnricher;
import com.walmartlabs.hawkshaw.clients.generic.util.HawkshawClientConstants;
import com.walmartlabs.hawkshaw.clients.generic.util.HawkshawHelpers;
import com.walmartlabs.hawkshaw.model.avro.CloudEvent;
import com.walmartlabs.hawkshaw.model.avro.HawkshawHeaders;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.util.StringUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class KafkaHawkshawPublisherTest {
  @Mock KafkaTemplate secureKafkaTemplate;
  @Spy private HawkshawEnricher hawkshawEnricher = hawkshawEnricher();
  @Mock TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks KafkaHawkshawPublisher kafkaHawkshawPublisher;
  @Mock RapidRelayerService rapidRelayerService;

  @BeforeMethod
  public void setUp() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(6085);
    TenantContext.setCorrelationId("test");
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testPublishWithHawkshaw() {

    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            "6085", ReceivingConstants.HAWKSHAW_ENABLED, false))
        .thenReturn(true);
    Map<String, Object> headers = getMockHeaders();

    kafkaHawkshawPublisher.publishKafkaWithHawkshaw(
        "test", "testmessage", "testtopic", headers, String.class.getName());

    verify(secureKafkaTemplate, times(1)).send(any(Message.class));

    final Object auditHeader = headers.get(ReceivingConstants.HAWKSHAW_HEADER);
    assertNotNull(auditHeader);
    final HawkshawHeaders hawkshawHeaders =
        HawkshawHelpers.bytesToHawkshawHeaders((byte[]) auditHeader);
    assertNotNull(hawkshawHeaders);

    final CloudEvent cloudEvent = hawkshawHeaders.getCloudevent();
    assertTrue(StringUtils.startsWithIgnoreCase(cloudEvent.getId().toString(), "urn:bkey:us:wm:"));
    assertThat(cloudEvent.getSource().toString(), is("urn:wm:source:US6085"));
    assertThat(cloudEvent.getTime(), is(notNullValue()));
    assertThat(cloudEvent.getType().toString(), is("java.lang.String"));
  }

  @Test
  public void testPublishWithOutbox() throws ReceivingException {

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    Map<String, Object> headers = getMockHeaders();

    kafkaHawkshawPublisher.publishKafkaWithHawkshaw(
        "test", "testmessage", "testtopic", headers, String.class.getName());
    verify(rapidRelayerService, times(1))
        .produceMessage(anyString(), anyString(), anyString(), anyMap());
  }

  @Test
  public void testPublishWithoutHawkshaw() {

    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    Map<String, Object> headers = getMockHeaders();

    kafkaHawkshawPublisher.publishKafkaWithHawkshaw(
        "test", "testmessage", "testtopic", headers, String.class.getName());

    verify(secureKafkaTemplate, times(1)).send(any(Message.class));

    final Object auditHeader = headers.get(ReceivingConstants.HAWKSHAW_HEADER);
    assertNull(auditHeader);
  }

  @Test
  public void testRuntimeException() {
    when(hawkshawEnricher.enrichOriginData(any(), any(), eq(false)))
        .thenThrow(new RuntimeException("Expected"));
    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            "6085", ReceivingConstants.HAWKSHAW_ENABLED, false))
        .thenReturn(true);
    Map<String, Object> headers = getMockHeaders();

    try {
      kafkaHawkshawPublisher.publishKafkaWithHawkshaw(
          "test", "testmessage", "testtopic", headers, String.class.getName());
      Assert.fail("Expected exception thrown");
    } catch (ReceivingInternalException e) {
      verify(secureKafkaTemplate, times(0)).send(any(Message.class));
    }
  }

  private static HawkshawEnricher hawkshawEnricher() {
    final Properties properties = new Properties();
    properties.setProperty(HawkshawClientConstants.PUBLISHER_ID, "pub.id");
    properties.setProperty(HawkshawClientConstants.SEQ_REGISTRY_URL, "seq.reg.id");
    properties.setProperty(HawkshawClientConstants.APP_ID, "app.id");
    properties.setProperty(HawkshawClientConstants.TENANT_ID, "test.tenant");

    try {
      return new HawkshawEnricher(properties);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Map<String, Object> getMockHeaders() {
    Map<String, Object> mockHttpHeaders = new HashMap<>();
    mockHttpHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, "32987");
    mockHttpHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, "us");
    mockHttpHeaders.put(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    mockHttpHeaders.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "1a2bc3d4");
    mockHttpHeaders.put(ReceivingConstants.SECURITY_HEADER_KEY, "1");
    mockHttpHeaders.put(ReceivingConstants.CONTENT_TYPE, "application/json");
    mockHttpHeaders.put(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    return mockHttpHeaders;
  }
}
