package com.walmart.move.nim.receiving.mfc.model.problem.lq;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ReasonAttributes {
  private String exceptionType;
  private String trailerSealNumber;
  private String shippingDate;
  private String documentType;
  private String routeNbr;
  private String loadNbr;
  private String recvCompletedTs;
  private String stopSquenceNbr;
  private String trailer;
  private String exceptionCategory;
  private String documentId;
  private String carrierId;
  private String shipmentNumber;
  private String deliveryNumber;
  private String trailerArrivalTs;
}
