package com.walmart.move.nim.receiving.core.entity;

import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import java.util.Date;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "LABEL_SEQUENCE")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class LabelSequence extends BaseMTEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "LABEL_SEQUENCE_SEQ")
  @SequenceGenerator(
      name = "LABEL_SEQUENCE_SEQ",
      sequenceName = "LABEL_SEQUENCE_SEQ",
      allocationSize = 100)
  @Column(name = "ID")
  private long id;

  @Column(name = "ITEM_NUMBER")
  private Long itemNumber;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "MUST_ARRIVE_BEFORE_DATE")
  private Date mustArriveBeforeDate;

  @Column(name = "PURCHASE_REFERENCE_LINE_NUMBER")
  private int purchaseReferenceLineNumber;

  @Column(name = "NEXT_SEQUENCE_NO")
  private Long nextSequenceNo;

  @Column(name = "LABEL_TYPE")
  @Enumerated(EnumType.ORDINAL)
  private LabelType labelType;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_CHANGE_TS")
  private Date lastChangeTs;

  @PreUpdate
  public void onUpdate() {
    this.lastChangeTs = new Date();
  }

  @PrePersist
  protected void onCreate() {
    this.createTs = new Date();
  }
}
