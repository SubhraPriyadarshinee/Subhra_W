package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.walmart.move.nim.receiving.core.model.RapidRelayerData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class RapidRelayerDataMapper {
  private static final String DELIMITER = "|";

  private RapidRelayerDataMapper() {}

  public static RapidRelayerData mapKafkaPublisherRequestoRapidRelayerData(
      String kafkaTopic,
      String publisherPolicyId,
      String msgKey,
      String msgBody,
      Map<String, Object> headers) {
    String eventIdentifier =
        headers.get(CORRELATION_ID_HEADER_KEY)
            + DELIMITER
            + headers.get(ReceivingConstants.TENENT_FACLITYNUM)
            + DELIMITER
            + headers.get(ReceivingConstants.TENENT_COUNTRY_CODE);

    return RapidRelayerData.builder()
        .eventIdentifier(eventIdentifier)
        .executionTs(Instant.now())
        .publisherPolicyId(publisherPolicyId)
        .headers(headers)
        .body(msgBody)
        .metaDataValues(getMetaDataValues(kafkaTopic, msgKey))
        .build();
  }

  private static Map<String, Object> getMetaDataValues(String kafkaTopic, String msgKey) {
    Map<String, Object> metaDataValues = new HashMap<>();
    metaDataValues.put(KAFKA_TOPIC, kafkaTopic);
    metaDataValues.put(KEY, msgKey);
    return metaDataValues;
  }

  private static Map<String, Object> getHttpMetaDataValues(String eventIdentifier) {
    Map<String, Object> metaDataValues = new HashMap<>();
    metaDataValues.put(KEY, eventIdentifier);
    return metaDataValues;
  }

  public static RapidRelayerData mapHttpPublisherRequestoRapidRelayerData(
      String publisherPolicyId, String msgBody, Map<String, Object> headers) {
    String eventIdentifier =
        headers.get(CORRELATION_ID_HEADER_KEY)
            + DELIMITER
            + headers.get(ReceivingConstants.TENENT_FACLITYNUM)
            + DELIMITER
            + headers.get(ReceivingConstants.TENENT_COUNTRY_CODE);

    return RapidRelayerData.builder()
        .eventIdentifier(eventIdentifier)
        .executionTs(Instant.now())
        .publisherPolicyId(publisherPolicyId)
        .headers(headers)
        .body(msgBody)
        .metaDataValues(getHttpMetaDataValues(eventIdentifier))
        .build();
  }
}
