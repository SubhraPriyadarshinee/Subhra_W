package com.walmart.move.nim.receiving.core.model.docktag;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DockTagInfo extends MessageData {
  private String trackingId;
  private String location;
  private Long deliveryNumber;
  private String containerType;
  private String skuIndicator;
  private Integer priority;
}
