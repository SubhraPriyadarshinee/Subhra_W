package com.walmart.move.nim.receiving.rc.entity;

import com.walmart.move.nim.receiving.core.entity.BaseMTEntity;
import com.walmart.move.nim.receiving.rc.contants.PackageTrackerCode;
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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "PACKAGE_RLOG")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class PackageRLog extends BaseMTEntity {
  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "PackageRLogSequence")
  @SequenceGenerator(name = "PackageRLogSequence", sequenceName = "package_rlog_sequence")
  private Long id;

  @Column(name = "PACKAGE_BARCODE_VALUE", length = 100)
  private String packageBarCodeValue;

  @Column(name = "PACKAGE_BARCODE_TYPE", length = 50)
  private String packageBarCodeType;

  @Column(name = "PACKAGE_COST")
  private Double packageCost;

  @Column(name = "IS_HIGH_VALUE", columnDefinition = "TINYINT")
  private Boolean isHighValue;

  @Column(name = "IS_SERIAL_SCAN_REQUIRED", columnDefinition = "TINYINT")
  private Boolean isSerialScanRequired;

  @Enumerated(EnumType.STRING)
  @Column(name = "REASON_CODE", length = 100)
  private PackageTrackerCode packageTrackerCode;

  @Column(name = "CREATE_USER", length = 50)
  private String createUser;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @PrePersist
  public void onCreate() {
    this.createTs = new Date();
  }
}
