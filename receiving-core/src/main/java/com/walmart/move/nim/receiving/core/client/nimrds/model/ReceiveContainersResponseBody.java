package com.walmart.move.nim.receiving.core.client.nimrds.model;

import java.util.List;
import lombok.Data;

@Data
public class ReceiveContainersResponseBody {

  private boolean sneEnabled;
  private List<ReceivedContainer> received;
  private List<Error> errors;
}
