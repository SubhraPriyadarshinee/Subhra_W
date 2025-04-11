package com.walmart.move.nim.receiving.core.message.common;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.List;
import lombok.*;
import org.springframework.http.HttpHeaders;

@Setter
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemUpdateMessage extends MessageData {
  private Integer itemNumber;
  private List<ActiveDeliveryMessage> activeDeliveries;
  private List<String> activePOs;
  private String eventType;
  private String previousCatalogGTIN;
  private String from;
  private String to;
  private Integer vnpk;
  private Integer whpk;
  private HttpHeaders httpHeaders;
}
