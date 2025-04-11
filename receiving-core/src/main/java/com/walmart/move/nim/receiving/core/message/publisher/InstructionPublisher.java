package com.walmart.move.nim.receiving.core.message.publisher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.model.PublishInstructionSummary;
import java.util.Date;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public abstract class InstructionPublisher {
  private static final Logger LOGGER = LoggerFactory.getLogger(InstructionPublisher.class);
  protected final Gson gson;

  public InstructionPublisher() {
    this.gson =
        new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
            .create();
  }

  protected Object getKafkaKey(PublishInstructionSummary publishInstructionSummary) {
    Object kafkaKey = "default_kafka_key";
    if (Objects.nonNull(publishInstructionSummary.getUserInfo())
        && !StringUtils.isEmpty(publishInstructionSummary.getUserInfo().getUserId()))
      kafkaKey = publishInstructionSummary.getUserInfo().getUserId();

    LOGGER.info("Kafka set for transaction is {}", kafkaKey);
    return kafkaKey;
  }
}
