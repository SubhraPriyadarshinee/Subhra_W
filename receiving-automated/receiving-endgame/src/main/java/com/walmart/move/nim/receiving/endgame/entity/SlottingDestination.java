package com.walmart.move.nim.receiving.endgame.entity;

import com.walmart.move.nim.receiving.core.entity.AuditableEntity;
import java.util.Date;
import java.util.Objects;
import javax.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * This is the entity to capture the sloting information based on UPC
 *
 * @author sitakant
 */
@Entity
@Table(name = "ENDGAME_UPC_DESTINATION")
@Getter
@Setter
@ToString
public class SlottingDestination extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ENDGAME_UPC_SLOTTING_SEQUENCE")
  @SequenceGenerator(
      name = "ENDGAME_UPC_SLOTTING_SEQUENCE",
      sequenceName = "ENDGAME_UPC_SLOTTING_SEQUENCE")
  @Column(name = "ID")
  private long id;

  @Column(name = "POSSIBLE_UPCS")
  private String possibleUPCs;

  @Column(name = "CASE_UPC", unique = true, length = 40)
  private String caseUPC;

  @Column(name = "DESTINATION")
  private String destination;

  @Column(name = "SELLER_ID")
  private String sellerId;

  @PreUpdate
  public void onUpdate() {
    this.setLastUpdatedDate(new Date());
  }

  @PrePersist
  protected void onCreate() {
    if (Objects.isNull(getLastUpdatedDate())) setLastUpdatedDate(new Date());
    if (Objects.isNull(getCreatedDate())) setCreatedDate(new Date());
  }
}
