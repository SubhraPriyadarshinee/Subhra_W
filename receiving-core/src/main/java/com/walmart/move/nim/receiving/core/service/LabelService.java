package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.printlabel.PrintJobResponse;
import com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelResponseBody;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;

public interface LabelService {

  public List<PrintJobResponse> getLabels(Long deliveryNumber, String userId, boolean labelsByUser);

  // To on-baord reprinting, market have to have this method implemented.
  public ReprintLabelResponseBody getReprintLabelData(
      Set<String> requestedTrackingIds, HttpHeaders httpHeaders);
}
