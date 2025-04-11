package com.walmart.move.nim.receiving.core.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.SumoConfig;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.mqtt.MqttNotificationData;
import com.walmart.move.nim.receiving.core.model.sumo.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.nio.charset.StandardCharsets;
import java.security.KeyRep;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.Resource;
import javax.persistence.Id;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Service for interacting with SUMO.
 *
 * @author r0s01us
 */
@Service
public class SumoService {
  private static final Logger LOGGER = LoggerFactory.getLogger(SumoService.class);

  @Autowired Gson gson;

  @ManagedConfiguration private SumoConfig sumoConfig;
  @ManagedConfiguration private AppConfig appConfig;

  @Resource(name = "retryableRestConnector")
  private RestConnector retryableRestConnector;

  private HttpHeaders headers;

  @Value("${sumo2.privatekey:default}")
  private String privateKey;

  @Value("${sumo.v2.private.key.version:999}")
  private String privateKeyVersion;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  /**
   * Sumo requires http basic auth and custom Accept header -- see
   * {http://amp.docs.walmart.com/sumo/api-overview.html}
   *
   * @return
   */
  private HttpHeaders getSumoHeaders() {
    headers = new HttpHeaders();
    String plainCreds = sumoConfig.getAppKey() + ":" + sumoConfig.getMasterKey();
    byte[] encodedCreds = Base64.getEncoder().encode(plainCreds.getBytes());
    headers.set(HttpHeaders.AUTHORIZATION, "Basic " + new String(encodedCreds));
    headers.set(HttpHeaders.ACCEPT, "application/vnd.walmart+json; version=1;");
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  /**
   * Creates a {@link SumoRequest} from the {@link SumoNotification} and {@code userId} arguments
   * and sends it as the payload of a REST request to Sumo. See
   * {http://amp.docs.walmart.com/sumo/api-references.html}
   */
  @Timed(
      name = "sendSumoNotificationTimed",
      level1 = "uwms-receiving",
      level2 = "sumoNotification",
      level3 = "sendSumoNotification")
  @ExceptionCounted(
      name = "sendSumoNotificationExceptionCount",
      level1 = "uwms-receiving",
      level2 = "sumoNotification",
      level3 = "sendSumoNotification")
  public ResponseEntity<String> sendNotificationToSumo(
      SumoNotification sumoNotification, List<String> userId) {
    SumoAudience sumoAudience = SumoAudience.builder().user_id(userId).build();
    String expiry =
        formatExpiryDateForSumo(
            Instant.now().plus(Duration.ofMinutes(sumoConfig.getExpirationMin())));
    SumoRequest sumoRequest = new SumoRequest(sumoNotification, sumoAudience, expiry);
    String body = gson.toJson(sumoRequest);
    LOGGER.info("Payload for SUMO 1.0: {}", sumoRequest);
    String url = sumoConfig.getBaseUrl() + ReceivingConstants.SUMO_API_PUSH;

    ResponseEntity<String> resp;
    try {
      resp = retryableRestConnector.post(url, body, getSumoHeaders(), String.class);
    } catch (RestClientResponseException e) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SUMO_REQ,
          String.format(
              ReceivingConstants.BAD_RESPONSE_ERROR_MSG,
              ReceivingConstants.SUMO,
              e.getRawStatusCode(),
              e.getResponseBodyAsString()));
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.SUMO_SERVICE_ERROR, ReceivingConstants.SUMO_SERVICE_DOWN);
    }

    LOGGER.info(ReceivingConstants.RESTUTILS_INFO_MESSAGE, url, body, resp.getBody());

    return resp;
  }

  public String formatExpiryDateForSumo(Instant instant) {
    DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    return formatter.format(instant);
  }

  public void setSumoPrivateProperties() throws IOException {
    Properties prop = readPropertiesFile(sumoConfig.getPrivateKeyLocation());
    LOGGER.info("sumo.v2.private.key.version: " + prop.getProperty("sumo.v2.private.key.version"));
    privateKey = prop.getProperty("sumo2.privatekey");
  }

  public static Properties readPropertiesFile(String fileName) throws IOException {
    FileInputStream fis = null;
    Properties prop = null;
    try {
      fis = new FileInputStream(fileName);
      prop = new Properties();
      prop.load(fis);
    } catch (IOException fnfe) {
      LOGGER.error("FileNotFoundException ", fnfe);
    } finally {
      if (fis != null) {
        fis.close();
      }
    }
    return prop;
  }
  /**
   * This method will call Sumo V2
   *
   * @param sumoNotification
   * @param userId
   * @return
   * @throws Exception
   */
  public ResponseEntity<String> sendNotificationToSumo2(
      SumoNotification sumoNotification, List<String> userId) throws Exception {

    setSumoPrivateProperties();
    SumoSite site = new SumoSite();
    site.setCountryCode(TenantContext.getFacilityCountryCode());
    site.setDomain(sumoConfig.getSumoDomain());
    site.setSiteId(TenantContext.getFacilityNum());
    site.setUserIds(userId);
    SumoAudience sumoAudience = SumoAudience.builder().sites(Arrays.asList(site)).build();

    String expiry =
        Instant.now().plus(Duration.ofMinutes(sumoConfig.getExpirationMin())).toString();
    SumoRequest sumoRequest = new SumoRequest(sumoNotification, sumoAudience, expiry);

    String body = gson.toJson(sumoRequest);
    String url = sumoConfig.getSumo2BaseUrl() + sumoConfig.getSumo2ContextPath();
    ResponseEntity<String> resp;
    try {
      LOGGER.info("sendNotificationToSumo2 Rest Call body: <" + body + ">");
      resp = retryableRestConnector.post(url, body, getSumo2Headers(), String.class);
    } catch (RestClientResponseException e) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SUMO_REQ,
          String.format(
              ReceivingConstants.BAD_RESPONSE_ERROR_MSG,
              ReceivingConstants.SUMO,
              e.getRawStatusCode(),
              e.getResponseBodyAsString()));
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.SUMO_SERVICE_ERROR, ReceivingConstants.SUMO_SERVICE_DOWN);
    }

    LOGGER.info(ReceivingConstants.RESTUTILS_INFO_MESSAGE, url, body, resp.getBody());

    return resp;
  }

  private HttpHeaders getSumo2Headers() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    long inTimestamp = System.currentTimeMillis();
    String consumerId = appConfig.getReceivingConsumerId();
    String authKey = loadSecrets().get(("sumo.app.key"));

    LOGGER.info("Sumo2 Private key version = {}", privateKeyVersion);
    httpHeaders.put(
        "WM_SEC.AUTH_SIGNATURE",
        Arrays.asList(buildAuthSignature(consumerId, authKey, inTimestamp, privateKeyVersion)));
    httpHeaders.put(
        ReceivingConstants.CONTENT_TYPE, Arrays.asList(ReceivingConstants.APPLICATION_JSON));
    httpHeaders.put("wm_app_uuid", Arrays.asList(sumoConfig.getSumo2AppUUId()));
    httpHeaders.put(ReceivingConstants.IQS_CONSUMER_ID_KEY, Arrays.asList(consumerId));
    httpHeaders.put("WM_CONSUMER.INTIMESTAMP", Arrays.asList(Long.toString(inTimestamp)));

    return httpHeaders;
  }

  private String buildAuthSignature(
      String consumerId, String consumerPrivateKey, long inTimestamp, String privateKeyVersion)
      throws Exception {

    String authSignature =
        getAuthSignature(inTimestamp, consumerId, consumerPrivateKey, privateKeyVersion);
    LOGGER.info("authSignature :{}", authSignature);
    return authSignature;
  }

  public Map<String, String> loadSecrets() {
    Map<String, String> secrets = new HashMap<>();
    secrets.put("sumo.app.key", privateKey);
    return secrets;
  }

  public String getAuthSignature(
      long inTimestamp, String consumerId, String privateKey, String privateKeyVersion)
      throws Exception {
    Map<String, String> map = new HashMap<>();
    map.put("WM_CONSUMER.ID", consumerId);
    map.put("WM_CONSUMER.INTIMESTAMP", Long.toString(inTimestamp));
    map.put("WM_SEC.KEY_VERSION", privateKeyVersion);
    String[] array = canonicalize(map);
    return generateSignature(privateKey, array[1]);
  }

  protected static String[] canonicalize(Map<String, String> headersToSign) {
    StringBuilder canonicalizedStrBuffer = new StringBuilder();
    StringBuilder parameterNamesBuffer = new StringBuilder();
    Set<String> keySet = headersToSign.keySet();

    // Create sorted key set to enforce order on the key names
    SortedSet<String> sortedKeySet = new TreeSet<>(keySet);
    for (String key : sortedKeySet) {
      Object val = headersToSign.get(key);
      parameterNamesBuffer.append(key.trim()).append(";");
      canonicalizedStrBuffer.append(val.toString().trim()).append("\n");
    }

    return new String[] {parameterNamesBuffer.toString(), canonicalizedStrBuffer.toString()};
  }

  public String generateSignature(String key, String stringToSign) throws Exception {
    Signature signatureInstance = Signature.getInstance("SHA256WithRSA");
    ServiceKeyRep keyRep =
        new ServiceKeyRep(KeyRep.Type.PRIVATE, "RSA", "PKCS#8", Base64.getDecoder().decode(key));
    PrivateKey resolvedPrivateKey = (PrivateKey) keyRep.readResolve();
    signatureInstance.initSign(resolvedPrivateKey);
    byte[] bytesToSign = stringToSign.getBytes(StandardCharsets.UTF_8);
    signatureInstance.update(bytesToSign);
    byte[] signatureBytes = signatureInstance.sign();
    return new String(Base64.getEncoder().encode(signatureBytes));
  }

  public void sendNotificationUsingMqtt(SumoNotification sumoNotification, List<String> userId) {
    try {

      SumoAudience sumoAudience = SumoAudience.builder().user_id(userId).build();
      String expiry =
          Instant.now().plus(Duration.ofMinutes(sumoConfig.getExpirationMin())).toString();
      SumoRequest sumoRequest = new SumoRequest(sumoNotification, sumoAudience, expiry);

      String payload = gson.toJson(sumoRequest);
      MqttNotificationData mqttNotificationData =
          MqttNotificationData.builder().payload(payload).build();
      LOGGER.info("Notification Payload to be sent via Mqtt: {}", payload);

      MessagePublisher messagePublisher =
          tenantSpecificConfigReader.getConfiguredInstance(
              String.valueOf(TenantContext.getFacilityNum()),
              ReceivingConstants.MQTT_NOTIFICATION_PUBLISHER,
              MessagePublisher.class);
      messagePublisher.publish(mqttNotificationData, null);

      LOGGER.info(
          "Notification: {} successfully published to topic: {}",
          payload,
          ReceivingConstants.PUB_MQTT_NOTIFICATIONS_TOPIC);

    } catch (Exception e) {
      LOGGER.error("Error while publishing notification using MQTT", e);
    }
  }

  class ServiceKeyRep extends KeyRep {

    private static final long serialVersionUID = -7213340660431987616L;

    public ServiceKeyRep(Type type, String algorithm, String format, byte[] encoded) {
      super(type, algorithm, format, encoded);
    }

    @Id
    @Override
    protected Object readResolve() throws ObjectStreamException {
      return super.readResolve();
    }
  }
}
