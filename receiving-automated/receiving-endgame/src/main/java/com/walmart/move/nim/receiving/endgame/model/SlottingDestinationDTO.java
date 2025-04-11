package com.walmart.move.nim.receiving.endgame.model;

import com.walmart.move.nim.receiving.core.entity.AuditableEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SlottingDestinationDTO extends AuditableEntity {

  private long id;

  private String possibleUPCs;

  private String caseUPC;

  private String destination;

  private String sellerId;
}
