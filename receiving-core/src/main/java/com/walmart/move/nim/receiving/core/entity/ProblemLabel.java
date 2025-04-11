/** */
package com.walmart.move.nim.receiving.core.entity;

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
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

/** @author a0b02ft */
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@Entity
@Table(name = "PROBLEM")
public class ProblemLabel extends BaseMTEntity {

  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "problemSequence")
  @SequenceGenerator(
      name = "problemSequence",
      sequenceName = "problem_sequence",
      allocationSize = 50)
  private Long id;

  @Column(length = 40, name = "PROBLEM_TAG_ID", unique = true, nullable = false)
  private String problemTagId;

  @Column(name = "DELIVERY_NUMBER", nullable = false)
  private Long deliveryNumber;

  @Column(length = 50, name = "ISSUE_ID", nullable = false)
  private String issueId;

  @Column(length = 50, name = "RESOLUTION_ID", nullable = true)
  private String resolutionId;

  @Column(length = 32, name = "PROBLEM_STATUS", nullable = false)
  private String problemStatus;

  @Column(length = 32, name = "CREATE_USER_ID")
  private String createUserId;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Column(length = 32, name = "LAST_CHANGE_USER_ID")
  private String lastChangeUserId;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_CHANGE_TS")
  private Date lastChangeTs;

  @Column(name = "PROBLEM_RESPONSE", columnDefinition = "nvarchar(max)")
  private String problemResponse;

  @PreUpdate
  public void onUpdate() {
    this.lastChangeTs = new Date();
  }

  @PrePersist
  protected void onCreate() {
    this.createTs = new Date();
    this.lastChangeTs = new Date();
  }
}
