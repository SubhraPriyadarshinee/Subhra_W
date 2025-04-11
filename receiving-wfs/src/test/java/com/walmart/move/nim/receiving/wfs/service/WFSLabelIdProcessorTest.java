package com.walmart.move.nim.receiving.wfs.service;

import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.model.LabelFormatId;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.wfs.constants.WFSConstants;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class WFSLabelIdProcessorTest extends ReceivingTestBase {

  @InjectMocks WFSLabelIdProcessor wfsLabelIdProcessor;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testGetLabelId() {
    assertEquals(
        wfsLabelIdProcessor.getLabelId(WFSConstants.WFS_PALLET, "PALLET"),
        LabelFormatId.WFS_PALLET_LABEL_FORMAT.getLabelId());
    assertEquals(
        wfsLabelIdProcessor.getLabelId(ReceivingConstants.DOCK_TAG, "PALLET"),
        LabelFormatId.CC_DOCK_TAG_LABEL_FORMAT.getLabelId());
    assertEquals(
        wfsLabelIdProcessor.getLabelId(WFSConstants.WFS_CASEPACK, "Vendor Pack"),
        LabelFormatId.WFS_CASEPACK_LABEL_FORMAT.getLabelId());
    assertEquals(wfsLabelIdProcessor.getLabelId("Dummy", "Dummy"), null);
  }
}
