package com.walmart.move.nim.receiving.wfs.controller;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doNothing;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.wfs.service.WFSCompleteDeliveryProcessor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WFSTestControllerTest {

  @Mock private WFSCompleteDeliveryProcessor wfsCompleteDeliveryProcessor;

  @InjectMocks private WFSTestController wfsTestController;

  private HttpHeaders headers;
  private static final String facilityNum = "4093";
  private static final String countryCode = "US";
  private String outputString = "WFS test controller completed successfully";

  @BeforeClass
  public void initMocks() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);
  }

  @BeforeMethod
  public void setUp() {}

  @AfterMethod
  public void resetMocks() {
    reset(wfsCompleteDeliveryProcessor);
  }

  @Test
  public void testExecuteSchedulerWFSAutoComplete() throws ReceivingException {
    doNothing().when(wfsCompleteDeliveryProcessor).autoCompleteDeliveries(any());
    String response = wfsTestController.executeSchedulerWFSAutoComplete();
    assertNotNull(response);
    assertEquals(outputString, response);
    verify(wfsCompleteDeliveryProcessor, times(1)).autoCompleteDeliveries(any());
  }
}
