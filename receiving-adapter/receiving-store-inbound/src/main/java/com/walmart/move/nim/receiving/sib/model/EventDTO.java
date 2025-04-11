package com.walmart.move.nim.receiving.sib.model;

import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class EventDTO implements Serializable {
  private Long id;

  private String key;

  private EventTargetStatus status;

  private String payload;

  private int retryCount;

  private Date pickUpTime;

  private EventType eventType;

  private Long deliveryNumber;

  private Map<String, Object> metaData;

  private Map<String, Object> additionalInfo;

  private String createdBy;

  private Date createdTime;
}
