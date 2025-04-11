package com.walmart.move.nim.receiving.acc.entity;

import com.walmart.move.nim.receiving.acc.model.NotificationSource;
import com.walmart.move.nim.receiving.core.entity.BaseMTEntity;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "NOTIFICATION_LOG")
@Getter
@Setter
@ToString
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog extends BaseMTEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "NOTIFICATION_LOG_SEQUENCE")
  @SequenceGenerator(
      name = "NOTIFICATION_LOG_SEQUENCE",
      sequenceName = "NOTIFICATION_LOG_SEQUENCE",
      allocationSize = 50)
  @Column(name = "ID")
  private Long id;

  // The time at which the message was received and logged
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LOG_TS")
  private Date logTs;

  // This is the response from sumo.
  @Column(name = "SUMO_RESPONSE", length = 255)
  private String sumoResponse;

  @Column(name = "TYPE")
  @Enumerated(EnumType.ORDINAL)
  private NotificationSource type;

  @Column(name = "LOCATION_ID", length = 255)
  private String locationId;

  // Following fields are come directly from the ACL Notification interface and should
  // be understood from the context of that message
  @Column(name = "NOTIFICATION_MESSAGE", columnDefinition = "nvarchar(max)")
  private String notificationMessage;

  @PrePersist
  protected void onCreate() {
    this.logTs = new Date();
  }
}
