package com.walmart.move.nim.receiving.core.service;

import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.model.LabelFormatId;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class CcLabelIdProcessorTest extends ReceivingTestBase {

  @InjectMocks CCLabelIdProcessor ccLabelIdProcessor;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void getLabelId() {
    assertEquals(
        ccLabelIdProcessor.getLabelId("DACon", "PALLET"),
        LabelFormatId.CC_DA_CON_PALLET_LABEL_FORMAT.getLabelId());
    assertEquals(
        ccLabelIdProcessor.getLabelId("PBYL", "PALLET"),
        LabelFormatId.CC_PBYL_PALLET_LABEL_FORMAT.getLabelId());
    assertEquals(
        ccLabelIdProcessor.getLabelId("SSTK", "PALLET"),
        LabelFormatId.CC_SSTK_PALLET_LABEL_FORMAT.getLabelId());
    assertEquals(
        ccLabelIdProcessor.getLabelId("DANonCon", "PALLET"),
        LabelFormatId.CC_DA_NON_CON_PALLET_LABEL_FORMAT.getLabelId());
    assertEquals(
        ccLabelIdProcessor.getLabelId("DSDC", "PALLET"),
        LabelFormatId.CC_NON_NATIONAL_PALLET_LABLE_FORMAT.getLabelId());
    assertEquals(
        ccLabelIdProcessor.getLabelId("POCON", "PALLET"),
        LabelFormatId.CC_NON_NATIONAL_PALLET_LABLE_FORMAT.getLabelId());
    assertEquals(
        ccLabelIdProcessor.getLabelId("DACon", "Vendor Pack"),
        LabelFormatId.CC_DA_CON_CASE_LABEL_FORMAT.getLabelId());
    assertEquals(
        ccLabelIdProcessor.getLabelId("ACL", "Vendor Pack"),
        LabelFormatId.CC_ACL_LABLE_FORMAT.getLabelId());
    assertEquals(ccLabelIdProcessor.getLabelId("Dummy", "Dummy"), null);
  }
}
