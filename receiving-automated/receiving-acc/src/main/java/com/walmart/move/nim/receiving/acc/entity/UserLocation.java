package com.walmart.move.nim.receiving.acc.entity;

import com.walmart.move.nim.receiving.acc.constants.LocationType;
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
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * This is the entity class for user and location mapping
 *
 * @author s0g015w
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "USER_LOCATION")
public class UserLocation extends BaseMTEntity {

  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "USER_LOCATION_SEQUENCE")
  @SequenceGenerator(
      name = "USER_LOCATION_SEQUENCE",
      sequenceName = "USER_LOCATION_SEQUENCE",
      allocationSize = 50)
  private Long id;

  @Column(name = "LOCATION_ID", nullable = false)
  private String locationId;

  @Column(name = "LOCATION_TYPE")
  @Enumerated(EnumType.ORDINAL)
  private LocationType locationType;

  @Column(name = "USER_ID", nullable = false)
  private String userId;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Column(name = "PARENT_LOCATION_ID")
  private String parentLocationId;

  @PrePersist
  protected void onCreate() {
    this.createTs = new Date();
  }
}
