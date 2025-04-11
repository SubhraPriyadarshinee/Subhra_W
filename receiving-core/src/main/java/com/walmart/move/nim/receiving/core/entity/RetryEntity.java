/** */
package com.walmart.move.nim.receiving.core.entity;

import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetFlow;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetType;
import java.util.Calendar;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Entity class for JMS Retry event.
 *
 * @author sitakant
 */
@Entity
@Table(name = "JMS_EVENT_RETRY")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class RetryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "jmsEventRetrySequence")
  @SequenceGenerator(
      name = "jmsEventRetrySequence",
      sequenceName = "jms_event_retry_sequence",
      allocationSize = 100)
  private Long id;

  @Column(name = "APPLICATION_TYPE")
  @Enumerated(EnumType.ORDINAL)
  private RetryTargetType retryTargetType;

  @Column(name = "RUNTIME_STATUS")
  @Enumerated(EnumType.ORDINAL)
  private EventTargetStatus eventTargetStatus;

  @Column(name = "APPLICATION_FLOW")
  @Enumerated(EnumType.ORDINAL)
  private RetryTargetFlow retryTargetFlow;

  @Lob
  @Column(name = "REQUEST_PAYLOAD")
  private String payload;

  @Column(name = "RETRIES_COUNT")
  private Long retriesCount;

  @Column(name = "JMS_QUEUE_NAME")
  private String jmsQueueName;

  // TODO Need to discuss
  @Column(name = "IS_ALERTED")
  private Boolean isAlerted;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_UPDATED_DATE")
  private Date lastUpdatedDate;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "FUTURE_PICKUP_TIME")
  private Date futurePickupTime;

  @PrePersist
  public void onCreate() {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(new Date());
    calendar.add(Calendar.SECOND, 60);
    this.futurePickupTime = calendar.getTime();
  }

  @PreUpdate
  public void onUpdate() {
    this.lastUpdatedDate = new Date();
  }
}
