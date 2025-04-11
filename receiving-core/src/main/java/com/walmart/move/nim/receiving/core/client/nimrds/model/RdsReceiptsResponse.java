package com.walmart.move.nim.receiving.core.client.nimrds.model;

import java.util.List;
import lombok.Data;

@Data
public class RdsReceiptsResponse {

  private List<Found> found;
  private List<Error> errors;
}
