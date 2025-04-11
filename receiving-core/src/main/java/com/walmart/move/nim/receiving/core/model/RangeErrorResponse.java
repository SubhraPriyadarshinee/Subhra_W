package com.walmart.move.nim.receiving.core.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Model to represent range error response (input which qualifies for overage reporting)
 *
 * @author g0k0072
 */
@Getter
public class RangeErrorResponse extends ErrorResponse {

  private int rcvdqtytilldate;
  private int maxReceiveQty;
  private int quantityCanBeReceived;
  private DeliveryDocument deliveryDocument;

  //  @Builder(builderMethodName = "rangeErrorBuilder")
  //  public RangeErrorResponse(String errorCode, Object errorMessage, String errorHeader, Object[]
  // values, String errorKey, String localisedErrorMessage, Object errorInfo, int rcvdqtytilldate,
  // int maxReceiveQty, int quantityCanBeReceived, DeliveryDocument deliveryDocument) {
  //    super(errorCode, errorMessage, errorHeader, values, errorKey, localisedErrorMessage,
  // errorInfo);
  //    this.rcvdqtytilldate = rcvdqtytilldate;
  //    this.maxReceiveQty = maxReceiveQty;
  //    this.quantityCanBeReceived = quantityCanBeReceived;
  //    this.deliveryDocument = deliveryDocument;
  //  }

  @Builder(builderMethodName = "rangeErrorBuilder")
  public RangeErrorResponse(
      ErrorResponse errorResponse,
      int rcvdqtytilldate,
      int maxReceiveQty,
      int quantityCanBeReceived,
      DeliveryDocument deliveryDocument) {
    super(
        errorResponse.getErrorCode(),
        errorResponse.getErrorMessage(),
        errorResponse.getErrorHeader(),
        errorResponse.getValues(),
        errorResponse.getErrorKey(),
        errorResponse.getLocalisedErrorMessage(),
        errorResponse.getErrorInfo());
    this.rcvdqtytilldate = rcvdqtytilldate;
    this.maxReceiveQty = maxReceiveQty;
    this.quantityCanBeReceived = quantityCanBeReceived;
    this.deliveryDocument = deliveryDocument;
  }

  /**
   * Range error thrown in create instruction flow
   *
   * @param errorCode
   * @param errorMessage
   * @param rcvdqtytilldate
   * @param maxReceiveQty
   */
  public RangeErrorResponse(
      String errorCode,
      Object errorMessage,
      int rcvdqtytilldate,
      int maxReceiveQty,
      DeliveryDocument deliveryDocument) {
    super(errorCode, errorMessage);
    this.rcvdqtytilldate = rcvdqtytilldate;
    this.maxReceiveQty = maxReceiveQty;
    this.deliveryDocument = deliveryDocument;
  }

  /**
   * Range error thrown in update instruction flow
   *
   * @param errorCode
   * @param errorMessage
   * @param quantityCanBeReceived
   */
  public RangeErrorResponse(String errorCode, Object errorMessage, int quantityCanBeReceived) {
    super(errorCode, errorMessage);
    this.quantityCanBeReceived = quantityCanBeReceived;
  }
}
