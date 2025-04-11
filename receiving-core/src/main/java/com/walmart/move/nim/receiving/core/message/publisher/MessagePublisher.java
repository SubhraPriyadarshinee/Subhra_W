package com.walmart.move.nim.receiving.core.message.publisher;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.Map;

public interface MessagePublisher<T extends MessageData> {
  void publish(T payload, Map<String, Object> messageHeader);
}
