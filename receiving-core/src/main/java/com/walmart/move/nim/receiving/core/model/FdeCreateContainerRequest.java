package com.walmart.move.nim.receiving.core.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class FdeCreateContainerRequest {
  private String messageId;
  private String trackingId;
  private String correlationId;
  private Facility facility;
  private String deliveryNumber;
  private String doorNumber;
  private String invoiceNumber;
  private String userId;
  private ContainerModel container;
}
