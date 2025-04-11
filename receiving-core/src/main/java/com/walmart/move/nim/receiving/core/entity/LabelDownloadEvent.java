package com.walmart.move.nim.receiving.core.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.Serializable;
import java.util.Date;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "LABEL_DOWNLOAD_EVENT")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabelDownloadEvent extends BaseMTEntity implements Serializable {
  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "LABEL_DOWNLOAD_EVENT_SEQUENCE")
  @SequenceGenerator(
      name = "LABEL_DOWNLOAD_EVENT_SEQUENCE",
      sequenceName = "LABEL_DOWNLOAD_EVENT_SEQUENCE",
      allocationSize = 100)
  private Long id;

  @Version
  @Column(name = "VERSION")
  private long version;

  @Column(name = "DELIVERY_NUMBER", columnDefinition = "bigint")
  private Long deliveryNumber;

  @Column(name = "PURCHASE_REFERENCE_NUMBER", length = 20)
  private String purchaseReferenceNumber;

  @Column(name = "ITEM_NUMBER", columnDefinition = "bigint")
  private Long itemNumber;

  @Column(name = "REJECT_REASON")
  @Enumerated(EnumType.ORDINAL)
  private RejectReason rejectReason;

  @Column(name = "STATUS", length = 20)
  private String status;

  @JsonFormat(pattern = ReceivingConstants.UTC_DATE_FORMAT)
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "PUBLISHED_TS")
  private Date publishedTs;

  @Column(name = "RETRY_COUNT")
  private int retryCount;

  @Column(name = "MESSAGE_PAYLOAD")
  private String messagePayload;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_CHANGE_TS")
  private Date lastChangeTS;

  @Column(name = "MISC_INFO")
  private String miscInfo;

  @PreUpdate
  public void onUpdate() {
    this.lastChangeTS = new Date();
  }

  @PrePersist
  protected void onCreate() {
    this.createTs = new Date();
  }
}
