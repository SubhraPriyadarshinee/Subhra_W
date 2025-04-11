package com.walmart.move.nim.receiving.core.common;

import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.model.FitProblemTagResponse;
import com.walmart.move.nim.receiving.core.model.Resolution;
import java.util.Arrays;
import org.mockito.InjectMocks;
import org.testng.annotations.Test;

public class ProblemUtilsTest extends ReceivingTestBase {
  @InjectMocks private ProblemUtils problemUtils;

  public void setUp() throws Exception {}

  public void tearDown() throws Exception {}

  @Test
  public void test_getMinimumResolutionQty() throws Exception {

    FitProblemTagResponse fitProblemTagResponse = createMockResponse();
    fitProblemTagResponse.setRemainingQty(20);
    final Integer lowerQtyInResolutionObjWins =
        ProblemUtils.getMinimumResolutionQty(fitProblemTagResponse);
    assertEquals(lowerQtyInResolutionObjWins.intValue(), 10);

    fitProblemTagResponse.setRemainingQty(5);
    final Integer lowerQtyAtHeaderWins =
        ProblemUtils.getMinimumResolutionQty(fitProblemTagResponse);
    assertEquals(lowerQtyAtHeaderWins.intValue(), 5);
  }

  private FitProblemTagResponse createMockResponse() {
    FitProblemTagResponse mockFitProblemTagResponse = new FitProblemTagResponse();
    Resolution mockResolution = new Resolution();
    mockResolution.setRemainingQty(10);
    mockFitProblemTagResponse.setResolutions(Arrays.asList(mockResolution));

    return mockFitProblemTagResponse;
  }
}
