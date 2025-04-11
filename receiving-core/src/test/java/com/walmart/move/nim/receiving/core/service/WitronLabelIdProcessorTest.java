package com.walmart.move.nim.receiving.core.service;

import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.model.LabelFormatId;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class WitronLabelIdProcessorTest extends ReceivingTestBase {

  @InjectMocks WitronLabelIdProcessor witronLabelIdProcessor;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void getLabelid() {
    assertEquals(
        witronLabelIdProcessor.getLabelId("SSTK", null),
        LabelFormatId.WITRON_PALLET_LABEL_FORMAT.getLabelId());
    assertEquals(witronLabelIdProcessor.getLabelId("DUMMY", null), null);
  }
}
