package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.OsdrConfigSpecification;

/**
 * This interface is responsible for providing market specific implementation for processing osdr
 * details.
 */
public interface OsdrProcessor {
  /**
   * This method has to implemented as per market specification
   *
   * @param osdrConfigSpecification
   */
  void process(OsdrConfigSpecification osdrConfigSpecification);
}
