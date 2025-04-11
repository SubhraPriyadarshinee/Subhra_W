package com.walmart.move.nim.receiving.sib.entity;

import com.walmart.move.nim.receiving.core.common.JpaConverterJson;
import com.walmart.move.nim.receiving.core.entity.AuditableEntity;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Transient;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "EVENT")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class Event extends AuditableEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "EVENT_SEQUENCE")
  @SequenceGenerator(name = "EVENT_SEQUENCE", sequenceName = "EVENT_SEQUENCE", allocationSize = 50)
  @Column(name = "ID")
  private Long id;

  @Column(name = "EVENT_KEY")
  private String key;

  @Column(name = "STATUS")
  @Enumerated(EnumType.STRING)
  private EventTargetStatus status;

  @Column(name = "PAYLOAD")
  String payload;

  @Column(name = "RETRY_COUNT")
  int retryCount;

  @Column(name = "PICKUP_TIME")
  private Date pickUpTime;

  @Column(name = "EVENT_TYPE")
  @Enumerated(EnumType.STRING)
  private EventType eventType;

  @Column(name = "DELIVERY_NUMBER")
  private Long deliveryNumber;

  @Column(name = "METADATA", length = 80)
  @Convert(converter = JpaConverterJson.class)
  private Map<String, Object> metaData;

  @Transient private Map<String, Object> additionalInfo;
}
