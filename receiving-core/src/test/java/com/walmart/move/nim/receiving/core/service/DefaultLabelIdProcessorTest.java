package com.walmart.move.nim.receiving.core.service;

import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DefaultLabelIdProcessorTest extends ReceivingTestBase {
  @InjectMocks DefaultLabelIdProcessor defaultLabelIdProcessor;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void getLabelid() {
    assertEquals(defaultLabelIdProcessor.getLabelId("DUMMY", null), null);
  }
}
