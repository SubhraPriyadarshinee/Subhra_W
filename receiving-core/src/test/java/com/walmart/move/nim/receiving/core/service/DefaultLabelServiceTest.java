package com.walmart.move.nim.receiving.core.service;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.util.TreeSet;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DefaultLabelServiceTest extends ReceivingTestBase {

  @InjectMocks DefaultLabelService defaultLabelService;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testGetLabels() {
    try {
      defaultLabelService.getLabels(98765L, "sysadmin", false);
      fail();
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  @Test
  public void testGetReprintLabelData() {
    try {
      defaultLabelService.getReprintLabelData(new TreeSet<>(), MockHttpHeaders.getHeaders());
      fail();
    } catch (Exception e) {
      assertTrue(true);
    }
  }
}
