package com.walmart.move.nim.receiving.core.model.symbotic;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpHeaders;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabelGroupUpdateCompletedEventMessage extends MessageData {
  private String deliveryNumber;
  private String groupType;
  private String locationId;
  private String status;
  private String inboundTagId;
  private String tagType;
  private HttpHeaders httpHeaders;
}
