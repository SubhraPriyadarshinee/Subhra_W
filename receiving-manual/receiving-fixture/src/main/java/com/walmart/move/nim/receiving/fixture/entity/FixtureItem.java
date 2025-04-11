package com.walmart.move.nim.receiving.fixture.entity;

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
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
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "FIXTURE_ITEM")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixtureItem {
  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "FIXTURE_ITEM_SEQUENCE")
  @SequenceGenerator(
      name = "FIXTURE_ITEM_SEQUENCE",
      sequenceName = "FIXTURE_ITEM_SEQUENCE",
      allocationSize = 50)
  private Long id;

  @Column(name = "ITEM_NUMBER")
  private Long itemNumber;

  @Column(name = "DESCRIPTION", length = 80)
  private String description;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @PrePersist
  protected void onCreate() {
    this.createTs = new Date();
  }
}
