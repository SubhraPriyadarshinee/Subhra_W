package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.model.ContainerItemDetails;
import com.walmart.move.nim.receiving.core.model.LabelAttributes;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DefaultReprintLabelHandlerTest {
  @InjectMocks private DefaultReprintLabelHandler defaultReprintLabelHandler;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test(expectedExceptions = ReceivingNotImplementedException.class)
  public void testPopulateReprintLabel() {
    defaultReprintLabelHandler.populateReprintLabel(
        new ContainerItemDetails(), new LabelAttributes());
  }
}
