package com.walmart.move.nim.receiving.sib.model.ei;

import java.util.Date;
import lombok.*;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class EventHeader {
  private String eventType;
  private String eventSubType;
  private String messageId;
  private String correlationId;
  private Date msgTimestamp;
  private Date eventCreationTime;
  private NodeInfo nodeInfo;
  private String originatorId;
  private String version;
}
