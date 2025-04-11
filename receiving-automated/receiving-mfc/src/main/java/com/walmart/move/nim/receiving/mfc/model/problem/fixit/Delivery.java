package com.walmart.move.nim.receiving.mfc.model.problem.fixit;

public class Delivery {

  private static final String TRAILER_NUMBER = "<TRAILER_NUMBER>";
  private static final String LOAD = "<LOAD>";
  private static final String SOURCE = "<SOURCE>";
  private static final String DELIVERY_NUMBER = "<DELIVERY_NUMBER>";

  private String request =
      " { number: \\\\\""
          + DELIVERY_NUMBER
          + "\\\\\" trailerNumber: \\\\\""
          + TRAILER_NUMBER
          + "\\\\\" load: \\\\\""
          + LOAD
          + "\\\\\" frieghtOriginCenter: \\\\\""
          + SOURCE
          + "\\\\\" }";

  public String getGraphQLString() {
    return request.replaceAll("<[a-z_]*>", "");
  }

  public void setTrailerNumber(String trailerNumber) {
    request = request.replaceAll(TRAILER_NUMBER, trailerNumber);
  }

  public void setLoad(String load) {
    request = request.replaceAll(LOAD, load);
  }

  public void setDeliveryNumber(String deliveryNumber) {
    request = request.replaceAll(DELIVERY_NUMBER, deliveryNumber);
  }

  public void setSource(String source) {
    request = request.replaceAll(SOURCE, source);
  }
}
