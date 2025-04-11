/** */
package com.walmart.move.nim.receiving.core.entity;

import com.walmart.move.nim.receiving.core.common.LabelIdentifierConverterJson;
import java.util.Date;
import java.util.Set;
import javax.persistence.Column;
import javax.persistence.Convert;
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
@Entity
@Table(name = "PRINTJOB")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class PrintJob extends BaseMTEntity {

  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "printJobSequence")
  @SequenceGenerator(
      name = "printJobSequence",
      sequenceName = "printjob_sequence",
      allocationSize = 50)
  private Long id;

  @Column(name = "DELIVERY_NUMBER", nullable = false)
  private Long deliveryNumber;

  @Column(name = "INSTRUCTION_ID", nullable = false)
  private Long instructionId;

  @Column(name = "LABEL_IDENTIFIER", nullable = false, columnDefinition = "nvarchar(max)")
  @Convert(converter = LabelIdentifierConverterJson.class)
  private Set<String> labelIdentifier;

  @Column(name = "COMPLETED_LABEL_IDENTIFIER", columnDefinition = "nvarchar(max)")
  @Convert(converter = LabelIdentifierConverterJson.class)
  private Set<String> completedLabelIdentifier;

  @Column(length = 32, name = "CREATE_USER_ID", nullable = false)
  private String createUserId;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "COMPLETE_TS")
  private Date completeTs;

  @PrePersist
  public void onCreate() {
    this.createTs = new Date();
  }

  @PreUpdate
  public void onUpdate() {
    if (this.labelIdentifier != null
        && this.completedLabelIdentifier != null
        && this.labelIdentifier.size() == this.completedLabelIdentifier.size()) {

      this.completeTs = new Date();
    } else {
      this.completeTs = null;
    }
  }
}
