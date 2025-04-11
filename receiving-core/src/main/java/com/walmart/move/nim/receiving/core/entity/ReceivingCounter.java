package com.walmart.move.nim.receiving.core.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "RECEIVING_COUNTER")
@Setter
@Getter
public class ReceivingCounter extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "RECEIVING_COUNTER_SEQUENCE")
  @SequenceGenerator(
      name = "RECEIVING_COUNTER_SEQUENCE",
      sequenceName = "RECEIVING_COUNTER_SEQUENCE")
  @Column(name = "ID")
  private Long id;

  @Column(name = "TYPE")
  private String type;

  @Column(name = "COUNTER_VALUE")
  private long counterNumber;

  @Column(name = "PREFIX")
  private char prefixIndex;

  @Transient private String prefix;

  @Version
  @Column(name = "VERSION")
  private long version;
}
