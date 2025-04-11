package com.walmart.move.nim.receiving.core.service.v2;

import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;

public interface CreateContainerProcessor {
  String createContainer(ContainerScanRequest containerScanRequest);
}
