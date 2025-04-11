package com.walmart.move.nim.receiving.rx.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.service.LPNCacheService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxLpnUtilsTest {
  @Mock private LPNCacheService rxLpnCacheService;
  @InjectMocks RxLpnUtils rxLpnUtils;
  private static final String LPN = "F06001003330000002";

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void tearDown() {
    reset(rxLpnCacheService);
  }

  @Test
  public void testRxGetLPN() throws ReceivingException {
    when(rxLpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(LPN));
    List<String> lpns = rxLpnUtils.get18DigitLPNs(1, MockHttpHeaders.getHeaders());
    assertEquals(lpns.get(0), LPN);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testGetLPN_Exception() throws ReceivingException {
    when(rxLpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class)))
        .thenReturn(new ArrayList<>());
    List<String> lpns = rxLpnUtils.get18DigitLPNs(1, MockHttpHeaders.getHeaders());
    assertNotNull(lpns);
  }
}
