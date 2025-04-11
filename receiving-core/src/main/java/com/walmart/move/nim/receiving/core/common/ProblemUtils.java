package com.walmart.move.nim.receiving.core.common;

import com.walmart.move.nim.receiving.core.model.FitProblemTagResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods operating on Problems, Fixit
 *
 * @author k0c0e5k
 */
public class ProblemUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProblemUtils.class);

  private ProblemUtils() {}

  /**
   * Quantity representing the actual remaining against the P-Tag irrespective of partially/fully
   * received is represented by value min value of two remainingQty at header and resolutions of
   * FixIT response
   *
   * @param fitProblemTagResponse
   * @return
   */
  public static Integer getMinimumResolutionQty(FitProblemTagResponse fitProblemTagResponse) {
    final Integer remainingQtyInHeader = fitProblemTagResponse.getRemainingQty();
    final Integer remainingQtyInResolution =
        fitProblemTagResponse.getResolutions().get(0).getRemainingQty();
    return remainingQtyInHeader > remainingQtyInResolution
        ? remainingQtyInResolution
        : remainingQtyInHeader;
  }
}
