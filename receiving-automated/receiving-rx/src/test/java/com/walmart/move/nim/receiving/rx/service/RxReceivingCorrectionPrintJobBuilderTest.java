package com.walmart.move.nim.receiving.rx.service;

import static org.testng.AssertJUnit.assertTrue;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import java.util.Map;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxReceivingCorrectionPrintJobBuilderTest {

  private RxReceivingCorrectionPrintJobBuilder rxReceivingCorrectionPrintJobBuilder =
      new RxReceivingCorrectionPrintJobBuilder();

  @BeforeMethod
  public void setUp() {
    ReflectionTestUtils.setField(rxReceivingCorrectionPrintJobBuilder, "gson", new Gson());
  }

  @Test
  public void testGetPrintJobForReceivingCorrection() throws Exception {
    Instruction instruction4mDB = MockInstruction.getRxCompleteInstruction();
    Map<String, Object> result =
        rxReceivingCorrectionPrintJobBuilder.getPrintJobForReceivingCorrection(
            1, ReceivingConstants.Uom.VNPK, instruction4mDB);
    List<Object> printRequests = (List<Object>) result.get("printRequests");
    PrintLabelRequest printLabelRequest = (PrintLabelRequest) printRequests.get(0);
    assertTrue(printLabelRequest.getData().contains(new LabelData("QTY", "1")));
    assertTrue(printLabelRequest.getData().contains(new LabelData("UOM", "ZA")));
  }
}
