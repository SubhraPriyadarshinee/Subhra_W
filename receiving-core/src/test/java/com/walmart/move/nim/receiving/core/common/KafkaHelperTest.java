package com.walmart.move.nim.receiving.core.common;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class KafkaHelperTest extends ReceivingTestBase {

  @Mock private KafkaConfig kafkaConfig;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);

    when(kafkaConfig.getKafkaSecurityProtocol()).thenReturn("SSL");
    when(kafkaConfig.getKafkaSSLKeyPassword()).thenReturn("333n4LGuVAtkN3a/uhCpEg==");
    when(kafkaConfig.getKafkaSSLKeystorePassword()).thenReturn("333n4LGuVAtkN3a/uhCpEg==");
    when(kafkaConfig.getKafkaSSLTruststorePassword()).thenReturn("333n4LGuVAtkN3a/uhCpEg==");
    when(kafkaConfig.getKafkaSSLTruststoreWcnpLocation())
        .thenReturn("kafkaSSLTruststoreWcnpLocation");
    when(kafkaConfig.getKafkaSSLKeystoreWcnpLocation()).thenReturn("kafkaSSLKeyStoreWcnpLocation");
    when(kafkaConfig.getKafkaSSLTruststoreLocation()).thenReturn("kafkaSSLTruststoreLocation");
    when(kafkaConfig.getKafkaSSLKeystoreLocation()).thenReturn("kafkaSSLKeystoreLocation");
    when(kafkaConfig.getTaasKafkaSSLTruststoreWcnpLocation())
        .thenReturn("taasKafkaSSLTruststoreWcnpLocation");
    when(kafkaConfig.getTaasKafkaSSLKeystoreWcnpLocation())
        .thenReturn("taasKafkaSSLKeystoreWcnpLocation");
  }

  @Test
  public void testBuildKafkaMessage() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
    assertNotNull(KafkaHelper.buildKafkaMessage(1234, "test", "test"));
  }

  @Test
  public void testBuildKafkaMessageWithCustomHeaders() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
    Map<String, Object> customHeaders = new HashMap<>();
    customHeaders.put("customKey", "customBalue");
    assertNotNull(KafkaHelper.buildKafkaMessage(1234, "test", "test", customHeaders));
  }

  @Test
  public void testBuildKafkaMessageWithTenantHeaders() {
    Map<String, Object> headers = new HashMap<>();
    headers.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "3ewewe-3eerer");
    headers.put(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, 32818);
    Message<String> kafkaMessage = KafkaHelper.buildKafkaMessage(1234, "test", "test", headers);
    assertNotNull(kafkaMessage);
    assertNotNull(kafkaMessage.getHeaders());
    assertEquals(kafkaMessage.getHeaders().get(KafkaHeaders.TOPIC), "test");
    assertEquals(kafkaMessage.getHeaders().get(KafkaHeaders.MESSAGE_KEY), "1234");
    assertEquals(
        kafkaMessage.getHeaders().get(ReceivingConstants.TENENT_COUNTRY_CODE), "US".getBytes());
    assertEquals(
        kafkaMessage.getHeaders().get(ReceivingConstants.TENENT_FACLITYNUM),
        String.valueOf(32818).getBytes());
    assertEquals(
        kafkaMessage.getHeaders().get(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        "3ewewe-3eerer".getBytes());
  }

  @Test
  public void testBuildKafkaMessageWithoutTenantHeaders() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
    Map<String, Object> headers = new HashMap<>();
    Message<String> kafkaMessage = KafkaHelper.buildKafkaMessage(1234, "test", "test", headers);
    assertNotNull(kafkaMessage);
    assertNotNull(kafkaMessage.getHeaders());
    assertNotNull(kafkaMessage.getHeaders().get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertEquals(kafkaMessage.getHeaders().get(ReceivingConstants.TENENT_FACLITYNUM), 32987);
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "MessageBody cannot be null ")
  public void testBuildKafkaMessageNullPayload() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
    assertNotNull(KafkaHelper.buildKafkaMessage(1234, null, "test"));
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Key Cannot be null")
  public void testBuildKafkaMessageNullKey() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
    assertNotNull(KafkaHelper.buildKafkaMessage(null, "test", "test"));
  }

  @Test
  public void testSetKafkaSecurityProps_falseWcnpDeployable_falseTaasKafka()
      throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException,
          IllegalBlockSizeException, NoSuchPaddingException {
    Map<String, Object> property = new HashMap<>();
    boolean isWcnpDeployable = false;
    boolean isTaasKafka = false;

    KafkaHelper.setKafkaSecurityProps(property, kafkaConfig, "AtlasReceivingKe", isWcnpDeployable);

    assertKafkaSecurityProps(property, isWcnpDeployable, isTaasKafka);
  }

  @Test
  public void testSetKafkaSecurityProps_trueWcnpDeployable_falseTaasKafka()
      throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException,
          IllegalBlockSizeException, NoSuchPaddingException {
    Map<String, Object> property = new HashMap<>();
    boolean isWcnpDeployable = true;
    boolean isTaasKafka = false;

    KafkaHelper.setKafkaSecurityProps(property, kafkaConfig, "AtlasReceivingKe", isWcnpDeployable);

    assertKafkaSecurityProps(property, isWcnpDeployable, isTaasKafka);
  }

  @Test
  public void testSetKafkaSecurityProps_trueWcnpDeployable_trueTaasKafka()
      throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException,
          IllegalBlockSizeException, NoSuchPaddingException {
    Map<String, Object> property = new HashMap<>();
    boolean isWcnpDeployable = true;
    boolean isTaasKafka = true;

    KafkaHelper.setKafkaSecurityProps(property, kafkaConfig, "AtlasReceivingKe", isWcnpDeployable);
    KafkaHelper.setKafkaPropsForTaas(property, kafkaConfig, isWcnpDeployable, isTaasKafka);

    assertKafkaSecurityProps(property, isWcnpDeployable, isTaasKafka);
  }

  @Test
  public void testSetKafkaSecurityProps_falseWcnpDeployable_trueTaasKafka()
      throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException,
          IllegalBlockSizeException, NoSuchPaddingException {
    Map<String, Object> property = new HashMap<>();
    boolean isWcnpDeployable = false;
    boolean isTaasKafka = true;

    KafkaHelper.setKafkaSecurityProps(property, kafkaConfig, "AtlasReceivingKe", isWcnpDeployable);
    KafkaHelper.setKafkaPropsForTaas(property, kafkaConfig, isWcnpDeployable, isTaasKafka);

    assertKafkaSecurityProps(property, isWcnpDeployable, isTaasKafka);
  }

  private void assertKafkaSecurityProps(
      Map<String, Object> property, boolean isWcnpDeployable, boolean isTaasKafka) {
    assertEquals(property.size(), 6);
    assertEquals("SSL", property.get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));

    if (isWcnpDeployable && !isTaasKafka) {
      assertEquals(
          "kafkaSSLTruststoreWcnpLocation",
          property.get(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
      assertEquals(
          "kafkaSSLKeyStoreWcnpLocation", property.get(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG));
    } else if (isWcnpDeployable && isTaasKafka) {
      assertEquals(
          "taasKafkaSSLTruststoreWcnpLocation",
          property.get(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
      assertEquals(
          "taasKafkaSSLKeystoreWcnpLocation",
          property.get(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG));
    } else {
      assertEquals(
          "kafkaSSLTruststoreLocation", property.get(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
      assertEquals(
          "kafkaSSLKeystoreLocation", property.get(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG));
    }
  }
}
