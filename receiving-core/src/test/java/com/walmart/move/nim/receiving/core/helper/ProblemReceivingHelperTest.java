package com.walmart.move.nim.receiving.core.helper;

import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.core.model.FitProblemTagResponse;
import com.walmart.move.nim.receiving.core.model.Issue;
import com.walmart.move.nim.receiving.core.model.Resolution;
import com.walmart.move.nim.receiving.utils.constants.ProblemResolutionState;
import com.walmart.move.nim.receiving.utils.constants.ProblemResolutionType;
import com.walmart.move.nim.receiving.utils.constants.ProblemStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

public class ProblemReceivingHelperTest {
  private FitProblemTagResponse isReceivableContainerProblemTagResponse =
      new FitProblemTagResponse();
  private ProblemReceivingHelper problemReceivingHelper = new ProblemReceivingHelper();

  @Test
  public void testIsContainerReceivable_whenIssueBusinessStatusIsNotReadyToReceive() {
    Issue issue1 = new Issue();
    issue1.setBusinessStatus("NOT_READY_TO_RECEIVE");
    isReceivableContainerProblemTagResponse.setIssue(issue1);
    boolean isContainerReceivable =
        problemReceivingHelper.isContainerReceivable(isReceivableContainerProblemTagResponse);
    assertFalse(isContainerReceivable);
  }

  @Test
  public void
      testIsContainerReceivable_whenAnsweredWithProperResolutionStatusButWithoutResolution() {
    Issue issue1 = new Issue();
    issue1.setBusinessStatus(ReceivingConstants.PROBLEM_STATUS_READY_TO_RECEIVE);
    issue1.setResolutionStatus(ProblemStatus.COMPLETE_RESOLUTON.toString());
    isReceivableContainerProblemTagResponse.setIssue(issue1);
    boolean isContainerReceivable =
        problemReceivingHelper.isContainerReceivable(isReceivableContainerProblemTagResponse);
    assertFalse(isContainerReceivable);
  }

  @Test
  public void testIsContainerReceivable_whenResolutionDoesNotContainPo() {
    Issue issue1 = new Issue();
    issue1.setBusinessStatus(ReceivingConstants.PROBLEM_STATUS_READY_TO_RECEIVE);
    issue1.setResolutionStatus(ProblemStatus.COMPLETE_RESOLUTON.toString());
    Resolution resolution = new Resolution();
    resolution.setId("1");
    resolution.setResolutionPoLineNbr(1);
    List<Resolution> resolutions = new ArrayList<>();
    resolution.setState(ProblemResolutionState.OPEN.toString());
    resolution.setType(ProblemResolutionType.ADDED_A_PO_LINE.toString());
    resolutions.add(resolution);
    isReceivableContainerProblemTagResponse.setIssue(issue1);
    isReceivableContainerProblemTagResponse.setResolutions(resolutions);
    boolean isContainerReceivable =
        problemReceivingHelper.isContainerReceivable(isReceivableContainerProblemTagResponse);
    assertFalse(isContainerReceivable);
  }

  @Test
  public void testIsContainerReceivable_whenResolutionDoesNotContainPoLine() {
    Issue issue1 = new Issue();
    issue1.setBusinessStatus(ReceivingConstants.PROBLEM_STATUS_READY_TO_RECEIVE);
    issue1.setResolutionStatus(ProblemStatus.COMPLETE_RESOLUTON.toString());
    Resolution resolution = new Resolution();
    resolution.setId("1");
    resolution.setResolutionPoNbr("1");
    List<Resolution> resolutions = new ArrayList<>();
    resolution.setState(ProblemResolutionState.OPEN.toString());
    resolution.setType(ProblemResolutionType.RECEIVE_AGAINST_ANOTHER_PO.toString());
    resolutions.add(resolution);
    isReceivableContainerProblemTagResponse.setIssue(issue1);
    isReceivableContainerProblemTagResponse.setResolutions(resolutions);
    boolean isContainerReceivable =
        problemReceivingHelper.isContainerReceivable(isReceivableContainerProblemTagResponse);
    assertFalse(isContainerReceivable);
  }

  @Test
  public void testIsContainerReceivable_whenContainerReceivable() {
    Issue issue1 = new Issue();
    issue1.setBusinessStatus(ReceivingConstants.PROBLEM_STATUS_READY_TO_RECEIVE);
    issue1.setResolutionStatus(ProblemStatus.COMPLETE_RESOLUTON.toString());
    Resolution resolution = new Resolution();
    resolution.setId("1");
    resolution.setResolutionPoLineNbr(1);
    resolution.setResolutionPoNbr("1");
    List<Resolution> resolutions = new ArrayList<>();
    resolution.setState(ProblemResolutionState.OPEN.toString());
    resolution.setType(ProblemResolutionType.RECEIVE_AGAINST_ANOTHER_PO.toString());
    resolutions.add(resolution);
    isReceivableContainerProblemTagResponse.setIssue(issue1);
    isReceivableContainerProblemTagResponse.setResolutions(resolutions);
    boolean isContainerReceivable =
        problemReceivingHelper.isContainerReceivable(isReceivableContainerProblemTagResponse);
    assertTrue(isContainerReceivable);
  }
}
