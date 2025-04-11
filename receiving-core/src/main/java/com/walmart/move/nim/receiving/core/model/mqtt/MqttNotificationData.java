package com.walmart.move.nim.receiving.core.model.mqtt;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MqttNotificationData extends MessageData {
  private String payload;
}
