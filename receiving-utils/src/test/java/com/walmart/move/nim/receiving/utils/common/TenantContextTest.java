package com.walmart.move.nim.receiving.utils.common;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DEFAULT_USER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.USER_ID_HEADER_KEY;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class TenantContextTest {
  @Test
  public void test_getSubcenterId() {
    assertEquals(TenantContext.getSubcenterId(), null);
    TenantContext.setSubcenterId(2);
    assertEquals(TenantContext.getSubcenterId(), new Integer(2));
  }

  @Test
  public void test_getOrgUnitId() {
    assertEquals(TenantContext.getOrgUnitId(), null);
    TenantContext.setOrgUnitId(2);
    assertEquals(TenantContext.getOrgUnitId(), new Integer(2));
  }

  @Test
  public void test_getUserId_default() {
    TenantContext.setAdditionalParams(USER_ID_HEADER_KEY, null);
    assertEquals(TenantContext.getUserId(), DEFAULT_USER);
  }

  @Test
  public void test_getUserId_WMT_hyphen_UserId() {
    TenantContext.setAdditionalParams(USER_ID_HEADER_KEY, "k0c0e5k");
    assertEquals(TenantContext.getUserId(), "k0c0e5k");
  }
}
