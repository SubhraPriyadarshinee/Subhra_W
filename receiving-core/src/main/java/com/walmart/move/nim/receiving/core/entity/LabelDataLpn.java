package com.walmart.move.nim.receiving.core.entity;

import java.util.Date;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "LABEL_DATA_LPN")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelDataLpn extends BaseMTEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "LABEL_DATA_LPN_SEQUENCE")
  @SequenceGenerator(
      name = "LABEL_DATA_LPN_SEQUENCE",
      sequenceName = "LABEL_DATA_LPN_SEQUENCE",
      allocationSize = 5000)
  @Column(name = "ID")
  private Long id;

  @Column(name = "LPN")
  private String lpn;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_CHANGE_TS")
  private Date lastChangeTs;

  @Column(name = "LABEL_DATA_ID")
  private Long labelDataId;

  @PreUpdate
  public void onUpdate() {
    this.lastChangeTs = new Date();
  }

  @PrePersist
  protected void onCreate() {
    this.createTs = new Date();
  }

  public static LabelDataLpn from(String lpn) {
    return LabelDataLpn.builder().lpn(lpn).build();
  }
}
