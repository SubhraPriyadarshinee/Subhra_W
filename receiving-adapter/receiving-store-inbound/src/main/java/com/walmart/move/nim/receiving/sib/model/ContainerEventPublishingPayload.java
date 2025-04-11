package com.walmart.move.nim.receiving.sib.model;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** POJO for stocking event published to inventory for status updates */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContainerEventPublishingPayload extends MessageData {
  private List<ContainerEventData> payload;
}
