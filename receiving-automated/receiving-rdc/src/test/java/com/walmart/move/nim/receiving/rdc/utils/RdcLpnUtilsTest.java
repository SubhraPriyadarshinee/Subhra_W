package com.walmart.move.nim.receiving.rdc.utils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

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

public class RdcLpnUtilsTest {
  @Mock private LPNCacheService rdcLpnCacheService;
  @InjectMocks RdcLpnUtils rdcLpnUtils;
  private static final String LPN = "F32818000020003005";

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void tearDown() {
    reset(rdcLpnCacheService);
  }

  @Test
  public void testGetLPN() throws ReceivingException {
    when(rdcLpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(LPN));
    List<String> lpns = rdcLpnUtils.getLPNs(1, MockHttpHeaders.getHeaders());
    assertEquals(lpns.get(0), LPN);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testGetLPN_Exception() throws ReceivingException {
    when(rdcLpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class)))
        .thenReturn(new ArrayList<>());
    List<String> lpns = rdcLpnUtils.getLPNs(1, MockHttpHeaders.getHeaders());
  }
}
