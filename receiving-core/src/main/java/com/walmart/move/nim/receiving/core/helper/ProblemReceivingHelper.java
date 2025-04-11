package com.walmart.move.nim.receiving.core.helper;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PROBLEM_STATUS_READY_TO_RECEIVE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PROBLEM_STATUS_RECEIVED;

import com.walmart.move.nim.receiving.core.model.FitProblemTagResponse;
import com.walmart.move.nim.receiving.core.model.Issue;
import com.walmart.move.nim.receiving.core.model.Resolution;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
public class ProblemReceivingHelper {
  private static final Logger log = LoggerFactory.getLogger(ProblemReceivingHelper.class);

  /**
   * Checks if a container with given problem tag is receivable or not
   *
   * @param fitProblemTagResponse problem tag response
   * @return boolean receivable or not
   */
  public boolean isContainerReceivable(FitProblemTagResponse fitProblemTagResponse) {
    Issue issue = fitProblemTagResponse.getIssue();
    List<Resolution> resolutions = fitProblemTagResponse.getResolutions();
    // Check if Problem is answered
    if (PROBLEM_STATUS_RECEIVED.equals(fitProblemTagResponse.getStatus())) {
      log.error(
          "Container is not receivable as problemtag status is already={}",
          fitProblemTagResponse.getStatus());
      return false;
    }
    if (!issue.getBusinessStatus().equalsIgnoreCase(PROBLEM_STATUS_READY_TO_RECEIVE)) {
      log.error(
          "Container is not receivable if issue business status: {}", issue.getBusinessStatus());
      return false;
    }

    // check if there is a resolution
    if (CollectionUtils.isEmpty(resolutions)) {
      log.error("Container is not receivable if resolutions array is empty");
      return false;
    }

    Resolution resolution = resolutions.get(0);

    // check if the PO/PO line information is there
    if (StringUtils.isEmpty(resolution.getResolutionPoNbr())
        || resolution.getResolutionPoLineNbr() == null) {
      log.error(
          "Container is not receivable if PO/PO Line info not present. PO: {}, PO Line: {}",
          resolution.getResolutionPoNbr(),
          resolution.getResolutionPoLineNbr());
      return false;
    }
    return true;
  }
}
